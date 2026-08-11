package com.company.s3explorer.ui.theme;

public record UITheme(String name, String className, boolean dark) {

    public static UITheme createThemeTitle(String name) {
        if (name == null) {
            return new UITheme("", "", false);
        }
        return new UITheme("----- " + name + " -----", "", false);
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean isDisabled() {
        return (className == null ||className.isEmpty());
    }
}
