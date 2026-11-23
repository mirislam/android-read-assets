package com.mirislam.readasset;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Local unit tests for helper logic in ReadAsset.
 */
public class ExampleUnitTest {

    @Test
    public void stripPdfExtension_removesOnlyTrailingExtension() {
        assertEquals("1_doc", MainActivity.stripPdfExtension("1_doc.pdf"));
        assertEquals("report", MainActivity.stripPdfExtension("report.PDF"));
        assertEquals("my_pdf_file", MainActivity.stripPdfExtension("my_pdf_file.pdf"));
        assertEquals("no_extension", MainActivity.stripPdfExtension("no_extension"));
    }
}
