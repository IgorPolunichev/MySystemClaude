package com.company.acts.mail.adapter.out.persistence;

import com.company.acts.mail.application.port.out.ContactDirectory;
import com.company.acts.mail.application.port.out.MessageStore;
import com.company.acts.mail.application.port.out.TemplateStore;
import com.company.acts.mail.application.view.MessageView;
import com.company.acts.mail.domain.MessageStatus;
import com.company.acts.mail.domain.SendMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Адресная книга, шаблоны, письма и вложения на SQL — три адаптера трёх портов. Списки адресов и актов хранятся
 * строками (адреса — через перевод строки, id актов — через запятую).
 */
final class JdbcMailStore {

    private JdbcMailStore() {
    }

    @Component
    static class Contacts implements ContactDirectory {

        private final JdbcTemplate jdbc;
        private final NamedParameterJdbcTemplate named;

        Contacts(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.named = new NamedParameterJdbcTemplate(jdbc);
        }

        // ---------- Контакты ----------

        @Override
        public List<Contact> search(String q, UUID organizationId, boolean includeInactive, int limit) {
            StringBuilder sql = new StringBuilder("select * from contact where true");
            MapSqlParameterSource p = new MapSqlParameterSource().addValue("limit", limit);
            if (!includeInactive) {
                sql.append(" and is_active");
            }
            if (organizationId != null) {
                sql.append(" and organization_id = :org");
                p.addValue("org", organizationId);
            }
            if (q != null) {
                sql.append(" and (lower(email) like :q or lower(coalesce(full_name, '')) like :q"
                        + " or lower(coalesce(organization_name, '')) like :q)");
                p.addValue("q", "%" + q.toLowerCase(java.util.Locale.ROOT) + "%");
            }
            sql.append(" order by lower(coalesce(full_name, email)) limit :limit");
            return named.query(sql.toString(), p, (rs, n) -> contact(rs));
        }

        @Override
        public Optional<Contact> find(UUID id) {
            return jdbc.query("select * from contact where id = ?", (rs, n) -> contact(rs), id).stream().findFirst();
        }

        @Override
        public Optional<Contact> findByEmail(String email) {
            return jdbc.query("select * from contact where lower(email) = lower(?)", (rs, n) -> contact(rs), email)
                    .stream().findFirst();
        }

        @Override
        public List<Contact> defaultsFor(Collection<UUID> organizationIds) {
            return named.query("select * from contact where is_active and is_default_for_organization"
                            + " and organization_id in (:ids) order by lower(coalesce(full_name, email))",
                    new MapSqlParameterSource("ids", organizationIds), (rs, n) -> contact(rs));
        }

        @Override
        public Contact save(Contact c) {
            jdbc.update("""
                    insert into contact (id, full_name, email, organization_id, organization_name, position, notes,
                        is_default_for_organization, is_active)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do update set full_name = excluded.full_name, email = excluded.email,
                        organization_id = excluded.organization_id, organization_name = excluded.organization_name,
                        position = excluded.position, notes = excluded.notes,
                        is_default_for_organization = excluded.is_default_for_organization,
                        is_active = excluded.is_active, updated_at = now()
                    """, c.id(), c.fullName(), c.email(), c.organizationId(), c.organizationName(), c.position(), c.notes(),
                    c.defaultForOrganization(), c.active());
            return find(c.id()).orElseThrow();
        }

        @Override
        public List<Group> groups() {
            List<Group> result = new ArrayList<>();
            jdbc.query("select id, name from contact_group order by lower(name)", rs -> {
                UUID id = rs.getObject("id", UUID.class);
                result.add(new Group(id, rs.getString("name"), members(id)));
            });
            return result;
        }

        @Override
        public Optional<Group> group(UUID id) {
            return jdbc.query("select id, name from contact_group where id = ?",
                    (rs, n) -> new Group(id, rs.getString("name"), members(id)), id).stream().findFirst();
        }

        private List<UUID> members(UUID groupId) {
            return jdbc.query("select contact_id from contact_group_member where group_id = ?",
                    (rs, n) -> rs.getObject("contact_id", UUID.class), groupId);
        }

        @Override
        public Group saveGroup(Group g) {
            jdbc.update("insert into contact_group (id, name) values (?, ?) on conflict (id) do update set name = excluded.name",
                    g.id(), g.name());
            jdbc.update("delete from contact_group_member where group_id = ?", g.id());
            for (UUID member : g.memberIds()) {
                jdbc.update("insert into contact_group_member (group_id, contact_id) values (?, ?)", g.id(), member);
            }
            return group(g.id()).orElseThrow();
        }

        @Override
        public void deleteGroup(UUID id) {
            jdbc.update("delete from contact_group where id = ?", id);
        }

        private static Contact contact(ResultSet rs) throws SQLException {
            return new Contact(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                    rs.getObject("organization_id", UUID.class), rs.getString("organization_name"), rs.getString("position"),
                    rs.getString("notes"), rs.getBoolean("is_default_for_organization"), rs.getBoolean("is_active"));
        }
    }

    @Component
    static class Templates implements TemplateStore {

        private final JdbcTemplate jdbc;
        private final NamedParameterJdbcTemplate named;

        Templates(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.named = new NamedParameterJdbcTemplate(jdbc);
        }

        // ---------- Шаблоны ----------

        @Override
        public List<Template> list() {
            return jdbc.query("select * from email_template order by is_default desc, lower(name)", (rs, n) -> template(rs));
        }

        @Override
        public Optional<Template> find(UUID id) {
            return jdbc.query("select * from email_template where id = ?", (rs, n) -> template(rs), id).stream().findFirst();
        }

        @Override
        public Optional<Template> findDefault() {
            return jdbc.query("select * from email_template where is_default", (rs, n) -> template(rs)).stream().findFirst();
        }

        @Override
        public Template save(Template t, String user) {
            if (t.isDefault()) {
                jdbc.update("update email_template set is_default = false where is_default and id <> ?", t.id());
            }
            jdbc.update("""
                    insert into email_template (id, name, subject_template, body_template, is_default, updated_by)
                    values (?, ?, ?, ?, ?, ?)
                    on conflict (id) do update set name = excluded.name, subject_template = excluded.subject_template,
                        body_template = excluded.body_template, is_default = excluded.is_default,
                        updated_by = excluded.updated_by, updated_at = now()
                    """, t.id(), t.name(), t.subject(), t.body(), t.isDefault(), user);
            return find(t.id()).orElseThrow();
        }

        @Override
        public void delete(UUID id) {
            jdbc.update("delete from email_template where id = ?", id);
        }

        private static Template template(ResultSet rs) throws SQLException {
            return new Template(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("subject_template"),
                    rs.getString("body_template"), rs.getBoolean("is_default"));
        }
    }

    @Component
    static class Messages implements MessageStore {

        private final JdbcTemplate jdbc;
        private final NamedParameterJdbcTemplate named;

        Messages(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.named = new NamedParameterJdbcTemplate(jdbc);
        }

        // ---------- Письма ----------

        @Override
        public void save(Message m, List<Attachment> attachments) {
            jdbc.update("""
                    insert into email_message (id, template_id, subject, body, recipients_to, recipients_cc, recipients_bcc,
                        reply_to, act_ids, act_numbers, mode, status, attempts, next_attempt_at, error, created_at,
                        created_by, sent_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, m.id(), m.templateId(), m.subject(), m.body(), lines(m.to()), lines(m.cc()), lines(m.bcc()),
                    m.replyTo(), String.join(",", m.actIds().stream().map(UUID::toString).toList()), lines(m.actNumbers()),
                    m.mode().name(), m.status().name(), m.attempts(), ts(m.nextAttemptAt()), m.error(), ts(m.createdAt()),
                    m.createdBy(), ts(m.sentAt()));
            int position = 1;
            for (Attachment a : attachments) {
                jdbc.update("insert into email_attachment (id, message_id, file_name, content_type, content, position)"
                        + " values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), m.id(), a.fileName(), a.contentType(),
                        a.content(), position++);
            }
        }

        @Override
        public Optional<Message> find(UUID id) {
            return jdbc.query("select * from email_message where id = ?", (rs, n) -> message(rs), id).stream().findFirst();
        }

        @Override
        public List<Attachment> attachments(UUID messageId) {
            return jdbc.query("select file_name, content_type, content from email_attachment where message_id = ?"
                            + " order by position",
                    (rs, n) -> new Attachment(rs.getString("file_name"), rs.getString("content_type"),
                            rs.getBytes("content")), messageId);
        }

        @Override
        public List<Message> lockDue(Instant now, int limit) {
            return jdbc.query("select * from email_message where status = 'QUEUED' and next_attempt_at <= ?"
                    + " order by next_attempt_at limit ? for update skip locked", (rs, n) -> message(rs), ts(now), limit);
        }

        @Override
        public void markSent(UUID id, Instant at) {
            jdbc.update("update email_message set status = 'SENT', sent_at = ?, attempts = attempts + 1, error = null,"
                    + " next_attempt_at = null where id = ?", ts(at), id);
        }

        @Override
        public void markAttempt(UUID id, int attempts, Instant nextAttemptAt, String error) {
            jdbc.update("update email_message set attempts = ?, next_attempt_at = ?, error = ?,"
                            + " status = case when cast(? as timestamptz) is null then 'FAILED' else status end where id = ?",
                    attempts, ts(nextAttemptAt), error, ts(nextAttemptAt), id);
        }

        @Override
        public List<MessageView> journal(UUID actId, int page, int size) {
            String where = actId == null ? "" : " where act_ids like ?";
            Object[] args = actId == null ? new Object[] {size, page * size}
                    : new Object[] {"%" + actId + "%", size, page * size};
            return jdbc.query("select * from email_message" + where + " order by created_at desc limit ? offset ?",
                    (rs, n) -> view(message(rs)), args);
        }

        @Override
        public long count(UUID actId) {
            Long count = actId == null ? jdbc.queryForObject("select count(*) from email_message", Long.class)
                    : jdbc.queryForObject("select count(*) from email_message where act_ids like ?", Long.class,
                    "%" + actId + "%");
            return count == null ? 0 : count;
        }

        private static MessageView view(Message m) {
            return new MessageView(m.id(), m.subject(), m.to(), m.cc(), m.actIds(), m.actNumbers(), m.mode(), m.status(),
                    m.attempts(), m.error(), m.createdAt(), m.createdBy(), m.sentAt());
        }

        private static Message message(ResultSet rs) throws SQLException {
            String ids = rs.getString("act_ids");
            return new Message(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                    rs.getString("subject"), rs.getString("body"), split(rs.getString("recipients_to")),
                    split(rs.getString("recipients_cc")), split(rs.getString("recipients_bcc")), rs.getString("reply_to"),
                    ids == null || ids.isBlank() ? List.of() : Arrays.stream(ids.split(",")).map(UUID::fromString).toList(),
                    split(rs.getString("act_numbers")), SendMode.valueOf(rs.getString("mode")),
                    MessageStatus.valueOf(rs.getString("status")), rs.getInt("attempts"), instant(rs, "next_attempt_at"),
                    rs.getString("error"), instant(rs, "created_at"), rs.getString("created_by"), instant(rs, "sent_at"));
        }

        private static String lines(List<String> list) {
            return list == null || list.isEmpty() ? "" : String.join("\n", list);
        }

        private static List<String> split(String value) {
            return value == null || value.isBlank() ? List.of() : List.of(value.split("\n"));
        }

        private static Timestamp ts(Instant i) {
            return i == null ? null : Timestamp.from(i);
        }

        private static Instant instant(ResultSet rs, String column) throws SQLException {
            Timestamp t = rs.getTimestamp(column);
            return t == null ? null : t.toInstant();
        }
    }
}
