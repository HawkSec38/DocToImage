package com.example.engine.samples

import com.example.data.model.DocumentType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates authentic binary sample documents (PPTX, DOCX, XLSX, MD, HTML, CSV)
 * with rich formatting to demonstrate direct-to-image rendering without PDF conversion.
 */
object SampleDocumentGenerator {

    data class SampleDocItem(
        val id: String,
        val title: String,
        val fileName: String,
        val documentType: DocumentType,
        val subtitle: String,
        val contentBytesProvider: () -> ByteArray
    )

    fun getSampleDocuments(): List<SampleDocItem> {
        return listOf(
            SampleDocItem(
                id = "sample_pptx",
                title = "NextGen Cloud Architecture",
                fileName = "Cloud_Architecture_2026.pptx",
                documentType = DocumentType.PPTX,
                subtitle = "3 Slides • Direct OOXML slide vector shapes & bullet cards",
                contentBytesProvider = { generateSamplePptx() }
            ),
            SampleDocItem(
                id = "sample_docx",
                title = "Enterprise Service Agreement",
                fileName = "Master_Services_Agreement.docx",
                documentType = DocumentType.DOCX,
                subtitle = "2 Pages • OOXML paragraphs, styled headers & tables",
                contentBytesProvider = { generateSampleDocx() }
            ),
            SampleDocItem(
                id = "sample_xlsx",
                title = "Q3 Financial Performance Model",
                fileName = "Financial_Forecast_Q3.xlsx",
                documentType = DocumentType.XLSX,
                subtitle = "Spreadsheet • Shared strings, formula ribbons & data matrix",
                contentBytesProvider = { generateSampleXlsx() }
            ),
            SampleDocItem(
                id = "sample_md",
                title = "Distributed Systems Whitepaper",
                fileName = "Architecture_Whitepaper.md",
                documentType = DocumentType.MARKDOWN,
                subtitle = "Markdown • Headings, code blocks, quote callouts & tables",
                contentBytesProvider = { generateSampleMarkdown() }
            ),
            SampleDocItem(
                id = "sample_html",
                title = "Client Billing Statement",
                fileName = "Corporate_Invoice_1084.html",
                documentType = DocumentType.HTML,
                subtitle = "HTML • Formatted typography, tabular items & totals",
                contentBytesProvider = { generateSampleHtml() }
            ),
            SampleDocItem(
                id = "sample_csv",
                title = "Global Inventory Ledger",
                fileName = "Warehouse_Inventory_2026.csv",
                documentType = DocumentType.CSV,
                subtitle = "CSV • Multi-column product SKUs, stock counts & pricing",
                contentBytesProvider = { generateSampleCsv() }
            )
        )
    }

    private fun generateSamplePptx(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
                    <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
                    <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
                    <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
                </Types>
            """.trimIndent())

            // Slide 1 (Title slide)
            addZipEntry(zos, "ppt/slides/slide1.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <p:spTree>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="914400" y="1500000"/><a:ext cx="7315200" cy="1800000"/></a:xfrm>
                                <a:solidFill><a:srgbClr val="F0F4F8"/></a:solidFill>
                                <a:prstGeom prst="roundRect"/>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="3200" b="1"><a:solidFill><a:srgbClr val="1A237E"/></a:solidFill></a:rPr>
                                        <a:t>Enterprise AI &amp; Cloud Platform</a:t>
                                    </a:r>
                                </a:p>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="1800" i="1"><a:solidFill><a:srgbClr val="455A64"/></a:solidFill></a:rPr>
                                        <a:t>High-Performance Direct Rendering Architecture</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="914400" y="3800000"/><a:ext cx="7315200" cy="800000"/></a:xfrm>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="1400"><a:solidFill><a:srgbClr val="78909C"/></a:solidFill></a:rPr>
                                        <a:t>Presented by Engineering Systems • Confidential Document</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:sld>
            """.trimIndent())

            // Slide 2 (Key Capabilities)
            addZipEntry(zos, "ppt/slides/slide2.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <p:spTree>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="685800" y="500000"/><a:ext cx="7772400" cy="800000"/></a:xfrm>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:r>
                                        <a:rPr sz="2600" b="1"><a:solidFill><a:srgbClr val="0D47A1"/></a:solidFill></a:rPr>
                                        <a:t>Core Architectural Pillars</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="685800" y="1600000"/><a:ext cx="7772400" cy="3000000"/></a:xfrm>
                                <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                                <a:prstGeom prst="roundRect"/>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:buChar char="•"/>
                                    <a:r>
                                        <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="212121"/></a:solidFill></a:rPr>
                                        <a:t>Zero PDF Intermediate Steps: </a:t>
                                    </a:r>
                                    <a:r>
                                        <a:rPr sz="1600"><a:solidFill><a:srgbClr val="424242"/></a:solidFill></a:rPr>
                                        <a:t>Directly rasters slides, text bodies, vector shapes to Bitmaps.</a:t>
                                    </a:r>
                                </a:p>
                                <a:p>
                                    <a:buChar char="•"/>
                                    <a:r>
                                        <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="212121"/></a:solidFill></a:rPr>
                                        <a:t>Lossless Typography &amp; Layout: </a:t>
                                    </a:r>
                                    <a:r>
                                        <a:rPr sz="1600"><a:solidFill><a:srgbClr val="424242"/></a:solidFill></a:rPr>
                                        <a:t>Preserves font sizes, bold/italic runs, colors and line geometries.</a:t>
                                    </a:r>
                                </a:p>
                                <a:p>
                                    <a:buChar char="•"/>
                                    <a:r>
                                        <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="212121"/></a:solidFill></a:rPr>
                                        <a:t>Lightweight Footprint: </a:t>
                                    </a:r>
                                    <a:r>
                                        <a:rPr sz="1600"><a:solidFill><a:srgbClr val="424242"/></a:solidFill></a:rPr>
                                        <a:t>Pure native Android Canvas and PullParser with 0 heavy native C++ binaries.</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:sld>
            """.trimIndent())

            // Slide 3 (Performance Metrics)
            addZipEntry(zos, "ppt/slides/slide3.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <p:spTree>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="685800" y="500000"/><a:ext cx="7772400" cy="800000"/></a:xfrm>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:r>
                                        <a:rPr sz="2600" b="1"><a:solidFill><a:srgbClr val="0D47A1"/></a:solidFill></a:rPr>
                                        <a:t>Benchmark Performance (2026)</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="685800" y="1600000"/><a:ext cx="3600000" cy="2400000"/></a:xfrm>
                                <a:solidFill><a:srgbClr val="E8EAF6"/></a:solidFill>
                                <a:prstGeom prst="roundRect"/>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="3600" b="1"><a:solidFill><a:srgbClr val="1A237E"/></a:solidFill></a:rPr>
                                        <a:t>18 ms</a:t>
                                    </a:r>
                                </a:p>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="1500"><a:solidFill><a:srgbClr val="283593"/></a:solidFill></a:rPr>
                                        <a:t>Average Slide Render Time</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="4800000" y="1600000"/><a:ext cx="3600000" cy="2400000"/></a:xfrm>
                                <a:solidFill><a:srgbClr val="E8F5E9"/></a:solidFill>
                                <a:prstGeom prst="roundRect"/>
                            </p:spPr>
                            <p:txBody>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="3600" b="1"><a:solidFill><a:srgbClr val="1B5E20"/></a:solidFill></a:rPr>
                                        <a:t>100%</a:t>
                                    </a:r>
                                </a:p>
                                <a:p>
                                    <a:pPr algn="ctr"/>
                                    <a:r>
                                        <a:rPr sz="1500"><a:solidFill><a:srgbClr val="2E7D32"/></a:solidFill></a:rPr>
                                        <a:t>Direct Image Fidelity</a:t>
                                    </a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:sld>
            """.trimIndent())
        }
        return baos.toByteArray()
    }

    private fun generateSampleDocx(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            addZipEntry(zos, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
            """.trimIndent())

            addZipEntry(zos, "word/document.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        <w:p>
                            <w:pPr><w:jc w:val="center"/></w:pPr>
                            <w:r>
                                <w:rPr><w:b/><w:sz w:val="48"/><w:color w:val="0D47A1"/></w:rPr>
                                <w:t>MASTER SERVICES AGREEMENT</w:t>
                            </w:r>
                        </w:p>
                        <w:p>
                            <w:pPr><w:jc w:val="center"/></w:pPr>
                            <w:r>
                                <w:rPr><w:i/><w:sz w:val="22"/><w:color w:val="546E7A"/></w:rPr>
                                <w:t>Contract Reference: MSA-2026-X99</w:t>
                            </w:r>
                        </w:p>
                        <w:p><w:r><w:t> </w:t></w:r></w:p>
                        <w:p>
                            <w:r>
                                <w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="1565C0"/></w:rPr>
                                <w:t>1. Parties and Engagement</w:t>
                            </w:r>
                        </w:p>
                        <w:p>
                            <w:r>
                                <w:rPr><w:sz w:val="24"/></w:rPr>
                                <w:t>This Master Agreement is executed between CloudScale Inc. ("Client") and Apex Systems Corp ("Provider"). The Provider agrees to deliver document rasterization services directly to image without converting through an intermediate portable document format.</w:t>
                            </w:r>
                        </w:p>
                        <w:p><w:r><w:t> </w:t></w:r></w:p>
                        <w:p>
                            <w:r>
                                <w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="1565C0"/></w:rPr>
                                <w:t>2. Statement of Deliverables &amp; Schedule</w:t>
                            </w:r>
                        </w:p>
                        <w:tbl>
                            <w:tr>
                                <w:tc><w:p><w:r><w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="0D47A1"/></w:rPr><w:t>Deliverable Phase</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="0D47A1"/></w:rPr><w:t>Specification</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="0D47A1"/></w:rPr><w:t>Delivery Date</w:t></w:r></w:p></w:tc>
                            </w:tr>
                            <w:tr>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Phase 1: Engine Core</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>DOCX &amp; PPTX direct parser</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Q1 2026</w:t></w:r></w:p></w:tc>
                            </w:tr>
                            <w:tr>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Phase 2: High-DPI Output</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>300 DPI Ultra-HD Bitmaps</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Q2 2026</w:t></w:r></w:p></w:tc>
                            </w:tr>
                            <w:tr>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Phase 3: Multi-Export</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Batch PNG and JPEG packaging</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:rPr><w:sz w:val="22"/></w:rPr><w:t>Q3 2026</w:t></w:r></w:p></w:tc>
                            </w:tr>
                        </w:tbl>
                        <w:p><w:r><w:t> </w:t></w:r></w:p>
                        <w:p>
                            <w:r>
                                <w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="1565C0"/></w:rPr>
                                <w:t>3. Warranty &amp; Performance SLA</w:t>
                            </w:r>
                        </w:p>
                        <w:p>
                            <w:r>
                                <w:rPr><w:sz w:val="24"/></w:rPr>
                                <w:t>Provider warrants that rendering operates 100% on-device without data transmission to third-party cloud servers, maintaining complete confidentiality of all uploaded documents.</w:t>
                            </w:r>
                        </w:p>
                    </w:body>
                </w:document>
            """.trimIndent())
        }
        return baos.toByteArray()
    }

    private fun generateSampleXlsx(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            addZipEntry(zos, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """.trimIndent())

            val strings = listOf(
                "Financial Line Item", "Q1 Actual", "Q2 Actual", "Q3 Forecast", "Q4 Target",
                "Subscription Revenue", "$1,450,000", "$1,820,000", "$2,240,000", "$2,780,000",
                "Professional Services", "$320,000", "$380,000", "$410,000", "$450,000",
                "Total Revenue", "$1,770,000", "$2,200,000", "$2,650,000", "$3,230,000",
                "Gross Margin %", "84.2%", "85.6%", "86.1%", "87.0%",
                "Operating Income", "$420,000", "$610,000", "$790,000", "$1,050,000"
            )

            val sharedXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            strings.forEach { sharedXml.append("<si><t>$it</t></si>") }
            sharedXml.append("</sst>")
            addZipEntry(zos, "xl/sharedStrings.xml", sharedXml.toString())

            // Sheet 1 XML
            val sheetXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            var strIdx = 0
            for (r in 1..6) {
                sheetXml.append("""<row r="$r">""")
                for (c in 0..4) {
                    val colLetter = ('A' + c).toString()
                    val cellRef = "$colLetter$r"
                    sheetXml.append("""<c r="$cellRef" t="s"><v>$strIdx</v></c>""")
                    strIdx++
                }
                sheetXml.append("</row>")
            }
            sheetXml.append("</sheetData></worksheet>")
            addZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml.toString())
        }
        return baos.toByteArray()
    }

    private fun generateSampleMarkdown(): ByteArray {
        val md = """
            # Cloud Infrastructure Specification
            **Document Version:** 4.2 • **Security:** Confidential
            
            ## Overview
            This document outlines the distributed pipeline for **real-time document conversion** directly to image artifacts without generating intermediate PDF containers.
            
            ### Architectural Tenets
            - **Deterministic Rasterization:** Pixel-identical layout across screen form factors.
            - **Zero Intermediate Storage:** Eliminates PDF disk thrashing and temporary file leakage.
            - **High Dynamic Resolution:** Native 300 DPI export capabilities.
            
            ```kotlin
            // Native Direct-to-Bitmap Pipeline
            val renderer = DirectDocumentRenderer(context)
            val renderedDoc = renderer.renderUri(docUri, "presentation.pptx")
            renderedDoc.pages.forEach { page ->
                saveBitmapAsPng(page.bitmap)
            }
            ```
            
            ### Pipeline Latency Comparison
            
            | Document Format | Direct Raster (ms) | PDF Converter (ms) | Speedup Factor |
            |-----------------|--------------------|--------------------|----------------|
            | PowerPoint PPTX | 42 ms              | 480 ms             | 11.4x          |
            | Word DOCX       | 28 ms              | 360 ms             | 12.8x          |
            | Excel XLSX      | 35 ms              | 410 ms             | 11.7x          |
            | Markdown / HTML | 12 ms              | 290 ms             | 24.1x          |
            
            > "By eliminating the PDF rendering middle-tier, memory consumption drops by 76% while rendering speed accelerates over 10x."
        """.trimIndent()
        return md.toByteArray(StandardCharsets.UTF_8)
    }

    private fun generateSampleHtml(): ByteArray {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <h1 style="color:#0D47A1;">INVOICE &amp; BILLING STATEMENT</h1>
                <p><b>Invoice Number:</b> INV-2026-8941<br/>
                <b>Issue Date:</b> September 23, 2026<br/>
                <b>Payment Terms:</b> Net 30 Days</p>
                <hr/>
                <h3>Billed To:</h3>
                <p>Acme Global Enterprise<br/>
                Attn: Accounts Payable<br/>
                742 Evergreen Parkway, Suite 900</p>
                
                <h3>Itemized Deliverables:</h3>
                <p><b>1. Direct Document Rendering Engine License</b> — $4,500.00<br/>
                <i>High performance offline OOXML rasterizer with multi-format support.</i></p>
                
                <p><b>2. High-DPI Output Module (300 DPI)</b> — $1,200.00<br/>
                <i>Ultra-sharp image export for publication and print layouts.</i></p>
                
                <p><b>3. Annual Technical Support &amp; Updates</b> — $800.00<br/>
                <i>24/7 priority developer SLA and quarterly format spec enhancements.</i></p>
                <hr/>
                <h2>Total Amount Due: $6,500.00 USD</h2>
                <p style="color:#2E7D32;"><b>Status: APPROVED &amp; READY FOR DISBURSEMENT</b></p>
            </body>
            </html>
        """.trimIndent()
        return html.toByteArray(StandardCharsets.UTF_8)
    }

    private fun generateSampleCsv(): ByteArray {
        val csv = """
            SKU,Item Description,Category,Warehouse Zone,Stock Qty,Unit Price,Total Valuation
            DOC-1001,Direct PPTX Vector Rasterizer,Software,Zone-A,140,$149.00,$20860.00
            DOC-1002,OOXML Word Paginator Engine,Software,Zone-A,220,$129.00,$28380.00
            DOC-1003,Excel Grid Matrix Module,Software,Zone-B,180,$119.00,$21420.00
            DOC-1004,Markdown / HTML Compiler,Software,Zone-B,310,$79.00,$24490.00
            DOC-1005,Rich Text RTF Tokenizer,Software,Zone-C,95,$89.00,$8455.00
            DOC-1006,Ultra-HD Image Export Bundle,Addon,Zone-C,450,$49.00,$22050.00
            TOTAL,,,,,1395,$125655.00
        """.trimIndent()
        return csv.toByteArray(StandardCharsets.UTF_8)
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }
}
