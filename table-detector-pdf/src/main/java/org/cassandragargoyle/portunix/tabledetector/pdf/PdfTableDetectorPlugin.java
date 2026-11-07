/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.cassandragargoyle.api.log.LogFactory;
import org.cassandragargoyle.portunix.tabledetector.api.DetectionContext;
import org.cassandragargoyle.portunix.tabledetector.api.DetectionResult;
import org.cassandragargoyle.portunix.tabledetector.api.TableDetectorPlugin;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;

import org.pf4j.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PDF table detection backend using PDFBox heuristic text position analysis
 *
 * @author Zdenek
 * @since 2026-02-03
 */
@Extension
public class PdfTableDetectorPlugin implements TableDetectorPlugin
{
	private static final Logger LOG = LogFactory.getLogger(PdfTableDetectorPlugin.class);

	private static final String[] SUPPORTED_MIME_TYPES =
	{
		"application/pdf"
	};

	@Override
	public String getId()
	{
		return "pdf";
	}

	@Override
	public String[] getSupportedMimeTypes()
	{
		return SUPPORTED_MIME_TYPES;
	}

	@Override
	public boolean supports(DetectionContext context)
	{
		if (context.getMimeType() != null)
		{
			for (String mimeType : SUPPORTED_MIME_TYPES)
			{
				if (mimeType.equalsIgnoreCase(context.getMimeType()))
				{
					return true;
				}
			}
		}

		// Fallback: check file extension
		if (context.getFileName() != null)
		{
			return context.getFileName().toLowerCase().endsWith(".pdf");
		}

		return false;
	}

	@Override
	public DetectionResult detect(DetectionContext context)
	{
		long startTime = System.currentTimeMillis();

		try
		{
			byte[] content = context.getContent();
			if (content == null || content.length == 0)
			{
				return DetectionResult.failure("No PDF content provided");
			}

			List<TableModel> allTables = new ArrayList<>();
			int tableCounter = 1;

			try (PDDocument document = Loader.loadPDF(content))
			{
				int pageCount = document.getNumberOfPages();

				int startPage = context.isAllPages() ? 1 : context.getPage();
				int endPage = context.isAllPages() ? pageCount : context.getPage();

				PdfTableDetector detector = new PdfTableDetector();

				for (int pageNum = startPage; pageNum <= endPage; pageNum++)
				{
					List<TableModel> pageTables = detector.detectTables(document, pageNum);

					for (TableModel table : pageTables)
					{
						table.setId("table-" + tableCounter);
						table.setPage(pageNum);
						tableCounter++;
					}

					allTables.addAll(pageTables);
				}
			}

			DetectionResult result = DetectionResult.success(allTables);
			result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
			result.addMetadata("table_count", String.valueOf(allTables.size()));

			return result;
		}
		catch (Exception e)
		{
			LOG.log(Level.SEVERE, "PDF table detection failed", e);
			return DetectionResult.failure("PDF table detection failed: " + e.getMessage());
		}
	}

	@Override
	public int getPriority()
	{
		return 10;
	}
}
