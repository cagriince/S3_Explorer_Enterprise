package com.company.s3explorer.ui.icons;

import java.util.List;

public record FileIconDefinition(
        String name,
        List<String> extensions,
        List<String> files) {
}