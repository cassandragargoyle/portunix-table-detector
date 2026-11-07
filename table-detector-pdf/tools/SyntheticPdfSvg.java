/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.cassandragargoyle.portunix.tabledetector.model.BoundingBox;
import org.cassandragargoyle.portunix.tabledetector.model.TableCell;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;
import org.cassandragargoyle.portunix.tabledetector.pdf.PdfTableDetector;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Renders the synthetic PDFs of PdfTableDetectorTest to SVG with the detected bounding boxes
 *
 * Usage: java -cp <test classpath> tools/SyntheticPdfSvg.java <output dir>
 */
public class SyntheticPdfSvg
{
	// Overlay styles: text cell, empty cell, table
	static final String TEXT = "stroke=\"#1f6feb\"";
	static final String EMPTY = "stroke=\"#e8590c\" stroke-dasharray=\"3 2\"";
	static final String TABLE = "stroke=\"#d1242f\" stroke-width=\"1.5\"";

	static String rect(BoundingBox b, String style)
	{
		return String.format(Locale.ROOT, "<rect %s x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"/>\n",
			style, b.getX(), b.getY(), b.getWidth(), b.getHeight());
	}

	public static void main(String[] args) throws Exception
	{
		Class<?> c = Class.forName("org.cassandragargoyle.portunix.tabledetector.pdf.PdfTableDetectorTest");
		Method m = c.getDeclaredMethod("createSyntheticPdf", PDRectangle.class, int.class, float.class, float.class);
		Method prose = c.getDeclaredMethod("createProsePdf");
		Method proseWithMatrix = c.getDeclaredMethod("createProseWithMatrixPdf");
		m.setAccessible(true);
		prose.setAccessible(true);
		proseWithMatrix.setAccessible(true);
		String out = args[0];
		float w = 14400f;

		// Same arguments as the tests in PdfTableDetectorTest: name, factory method, its arguments
		Object[][] variants = {
			{"synthetic-a4", m, new Object[] {PDRectangle.A4, 0, 0f, 750f}},
			{"synthetic-rotated-90", m, new Object[] {PDRectangle.A4, 90, 0f, 750f}},
			{"synthetic-rotated-180", m, new Object[] {PDRectangle.A4, 180, 0f, 750f}},
			{"synthetic-rotated-270", m, new Object[] {PDRectangle.A4, 270, 0f, 750f}},
			{"synthetic-offset-mediabox", m, new Object[] {new PDRectangle(100, 100, 595, 842), 0, 100f, 850f}},
			{"synthetic-max-coordinate", m, new Object[] {new PDRectangle(w, 842), 0, w - 400f - 100f, 750f}},
			{"synthetic-prose", prose, new Object[0]},
			{"synthetic-prose-with-matrix", proseWithMatrix, new Object[0]},
		};
		for (Object[] v : variants)
		{
			String name = (String) v[0];
			File pdf = new File(out, name + ".pdf");
			StringBuilder overlay = new StringBuilder("<g id=\"detected\" fill=\"none\" stroke-width=\"0.8\">\n");
			try (PDDocument d = (PDDocument) ((Method) v[1]).invoke(null, (Object[]) v[2]))
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
			File svg = new File(out, name + ".svg");
			new ProcessBuilder("pdftocairo", "-svg", pdf.getPath(), svg.getPath()).inheritIO().start().waitFor();
			String style = "<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n";
			String content = Files.readString(svg.toPath())
				.replaceFirst("<defs>", Matcher.quoteReplacement(style) + "<defs>")
				.replace("</svg>", overlay + "</svg>");
			Files.writeString(svg.toPath(), content);
		}
	}
}
