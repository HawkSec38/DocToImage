package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentType
import com.example.data.model.RenderOptions
import com.example.engine.DirectDocumentRenderer
import com.example.engine.samples.SampleDocumentGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `appName string matches DocToImage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DocToImage", appName)
    }

    @Test
    fun `direct rendering of PPTX without PDF conversion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = DirectDocumentRenderer(context)
        val samples = SampleDocumentGenerator.getSampleDocuments()
        val pptxSample = samples.first { it.documentType == DocumentType.PPTX }

        val doc = renderer.renderBytes(
            bytes = pptxSample.contentBytesProvider(),
            fileName = pptxSample.fileName,
            options = RenderOptions()
        )

        assertNotNull(doc)
        assertEquals(DocumentType.PPTX, doc.documentType)
        assertTrue("PPTX should have rendered multiple slides", doc.pages.size >= 3)
        doc.pages.forEach { page ->
            assertNotNull(page.bitmap)
            assertTrue("Page bitmap width should be positive", page.widthPx > 0)
            assertTrue("Page bitmap height should be positive", page.heightPx > 0)
        }
    }

    @Test
    fun `direct rendering of DOCX without PDF conversion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = DirectDocumentRenderer(context)
        val samples = SampleDocumentGenerator.getSampleDocuments()
        val docxSample = samples.first { it.documentType == DocumentType.DOCX }

        val doc = renderer.renderBytes(
            bytes = docxSample.contentBytesProvider(),
            fileName = docxSample.fileName,
            options = RenderOptions()
        )

        assertNotNull(doc)
        assertEquals(DocumentType.DOCX, doc.documentType)
        assertTrue("DOCX should have rendered pages", doc.pages.isNotEmpty())
        assertNotNull(doc.pages.first().bitmap)
    }

    @Test
    fun `direct rendering of XLSX without PDF conversion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = DirectDocumentRenderer(context)
        val samples = SampleDocumentGenerator.getSampleDocuments()
        val xlsxSample = samples.first { it.documentType == DocumentType.XLSX }

        val doc = renderer.renderBytes(
            bytes = xlsxSample.contentBytesProvider(),
            fileName = xlsxSample.fileName,
            options = RenderOptions()
        )

        assertNotNull(doc)
        assertEquals(DocumentType.XLSX, doc.documentType)
        assertTrue("XLSX should have rendered pages", doc.pages.isNotEmpty())
    }
}
