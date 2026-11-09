/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.cassandragargoyle.portunix.tabledetector.model.BoundingBox;
import org.cassandragargoyle.portunix.tabledetector.model.TableCell;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Renders the synthetic PDFs of PdfTableDetectorTest to SVG with the detected bounding boxes
 *
 * Usage: java -cp <test classpath> org.cassandragargoyle.portunix.tabledetector.pdf.SyntheticPdfSvg <output dir>
 */
public class SyntheticPdfSvg
{
	// Overlay styles: text cell, empty cell, table
	static final String TEXT = "stroke=\"#1f6feb\"";
	static final String EMPTY = "stroke=\"#e8590c\" stroke-dasharray=\"3 2\"";
	static final String TABLE = "stroke=\"#d1242f\" stroke-width=\"1.5\"";

	// Factory of one synthetic document
	interface Factory
	{
		PDDocument create() throws IOException;
	}

	// Output file name without extension and the factory of its document
	record Variant(String name, Factory factory)
	{
	}

	static String rect(BoundingBox b, String style)
	{
		return String.format(Locale.ROOT, "<rect %s x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"/>\n",
			style, b.getX(), b.getY(), b.getWidth(), b.getHeight());
	}

	public static void main(String[] args) throws Exception
	{
		String out = args[0];
		float w = 14400f;

		// Same arguments as the tests in PdfTableDetectorTest
		List<Variant> variants = List.of(
			new Variant("synthetic-a4", () -> PdfTableDetectorTest.createSyntheticPdf(PDRectangle.A4, 0, 0f, 750f)),
			new Variant("synthetic-rotated-90", () -> PdfTableDetectorTest.createSyntheticPdf(PDRectangle.A4, 90, 0f, 750f)),
			new Variant("synthetic-rotated-180", () -> PdfTableDetectorTest.createSyntheticPdf(PDRectangle.A4, 180, 0f, 750f)),
			new Variant("synthetic-rotated-270", () -> PdfTableDetectorTest.createSyntheticPdf(PDRectangle.A4, 270, 0f, 750f)),
			new Variant("synthetic-offset-mediabox",
				() -> PdfTableDetectorTest.createSyntheticPdf(new PDRectangle(100, 100, 595, 842), 0, 100f, 850f)),
			new Variant("synthetic-max-coordinate",
				() -> PdfTableDetectorTest.createSyntheticPdf(new PDRectangle(w, 842), 0, w - 400f - 100f, 750f)),
			new Variant("synthetic-prose", PdfTableDetectorTest::createProsePdf),
			new Variant("synthetic-prose-with-matrix", PdfTableDetectorTest::createProseWithMatrixPdf));
		for (Variant v : variants)
		{
			File pdf = new File(out, v.name() + ".pdf");
			StringBuilder overlay = new StringBuilder("<g id=\"detected\" fill=\"none\" stroke-width=\"0.8\">\n");
			try (PDDocument d = v.factory().create())
			{
				d.save(pdf);
				List<TableModel> tables = new PdfTableDetector().detectTables(d, 1);
				for (TableModel t : tables)
				{
					for (TableCell cell : t.getCells())
					{
						if (cell.getBoundingBox() != null)
						{
							overlay.append(rect(cell.getBoundingBox(), cell.getText().isBlank() ? EMPTY : TEXT));
						}
					}
					if (t.getBoundingBox() != null)
					{
						overlay.append(rect(t.getBoundingBox(), TABLE));
					}
				}
			}
			overlay.append("</g>\n");
			File svg = new File(out, v.name() + ".svg");
			new ProcessBuilder("pdftocairo", "-svg", pdf.getPath(), svg.getPath()).inheritIO().start().waitFor();
			String style = "<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n";
			String content = Files.readString(svg.toPath())
				.replaceFirst("<defs>", Matcher.quoteReplacement(style) + "<defs>")
				.replace("</svg>", overlay + "</svg>");
			Files.writeString(svg.toPath(), content);
		}
	}
}
