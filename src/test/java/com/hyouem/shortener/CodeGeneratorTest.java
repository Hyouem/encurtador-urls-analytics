package com.hyouem.shortener;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesNineAlphanumericCharacters() {
        assertTrue(generator.generate().matches("[a-zA-Z0-9]{9}"));
    }

    @Test
    void generatesDistinctCodesInSample() {
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            assertTrue(codes.add(generator.generate()));
        }
    }
}