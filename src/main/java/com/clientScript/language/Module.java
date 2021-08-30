package com.clientScript.language;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class Module {
    private String name;
    private String code;

    public Module(Path sourcePath) {
        try {
            name = sourcePath.getFileName().toString().replaceFirst("\\.scl?","").toLowerCase(Locale.ROOT);
            code = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            name = null;
            code = null;
        }
    }

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }
}
