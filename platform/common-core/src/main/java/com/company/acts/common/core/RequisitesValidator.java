package com.company.acts.common.core;

import java.util.regex.Pattern;

/** Проверка реквизитов российских организаций: ИНН, КПП, ОГРН (с контрольными цифрами). */
public final class RequisitesValidator {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern KPP = Pattern.compile("\\d{4}[\\dA-Z]{2}\\d{3}");
    private static final int[] INN10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_1 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_2 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    private RequisitesValidator() {
    }

    public static boolean isValidInn(String inn) {
        if (inn == null || !DIGITS.matcher(inn).matches()) {
            return false;
        }
        if (inn.length() == 10) {
            return checkDigit(inn, INN10) == digit(inn, 9);
        }
        if (inn.length() == 12) {
            return checkDigit(inn, INN12_1) == digit(inn, 10) && checkDigit(inn, INN12_2) == digit(inn, 11);
        }
        return false;
    }

    public static boolean isValidKpp(String kpp) {
        return kpp != null && KPP.matcher(kpp).matches();
    }

    public static boolean isValidOgrn(String ogrn) {
        if (ogrn == null || !DIGITS.matcher(ogrn).matches()) {
            return false;
        }
        if (ogrn.length() == 13) {
            long base = Long.parseLong(ogrn.substring(0, 12));
            return (base % 11) % 10 == digit(ogrn, 12);
        }
        if (ogrn.length() == 15) {
            long base = Long.parseLong(ogrn.substring(0, 14));
            return (base % 13) % 10 == digit(ogrn, 14);
        }
        return false;
    }

    private static int checkDigit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i] * digit(value, i);
        }
        return (sum % 11) % 10;
    }

    private static int digit(String value, int index) {
        return value.charAt(index) - '0';
    }
}
