package ch.so.agi.gdal.ffm.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreationOptionListParserTest {
    @Test
    void shouldExtractCompressValuesFromCreationOptionList() {
        String xml = """
                <CreationOptionList>
                  <Option name="COMPRESS" type="string-select" default="NONE">
                    <Value>NONE</Value>
                    <Value>LZW</Value>
                    <Value>DEFLATE</Value>
                    <Value>ZSTD</Value>
                  </Option>
                </CreationOptionList>
                """;

        List<String> values = CreationOptionListParser.enumValues(xml, "COMPRESS");

        assertEquals(List.of("NONE", "LZW", "DEFLATE", "ZSTD"), values);
    }

    @Test
    void shouldReturnEmptyListForUnknownOption() {
        String xml = """
                <CreationOptionList>
                  <Option name="TILED" type="boolean"/>
                </CreationOptionList>
                """;

        assertTrue(CreationOptionListParser.enumValues(xml, "COMPRESS").isEmpty());
    }

    @Test
    void shouldReturnEmptyListForInvalidXml() {
        assertTrue(CreationOptionListParser.enumValues("<CreationOptionList>", "COMPRESS").isEmpty());
    }
}
