package com.company.acts.common.importing;

import java.util.ArrayList;
import java.util.List;

/** Итог импорта: сколько создано, обновлено, пропущено и ошибки по строкам. */
public final class ImportResult {

    public record RowError(int row, String message) {
    }

    private int created;
    private int updated;
    private int skipped;
    private final List<RowError> errors = new ArrayList<>();

    public void created() {
        created++;
    }

    public void updated() {
        updated++;
    }

    public void skipped() {
        skipped++;
    }

    public void error(int row, String message) {
        errors.add(new RowError(row, message));
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<RowError> getErrors() {
        return List.copyOf(errors);
    }
}
