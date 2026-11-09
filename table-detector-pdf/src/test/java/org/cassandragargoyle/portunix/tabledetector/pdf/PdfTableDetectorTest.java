/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.pdf;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.cassandragargoyle.portunix.tabledetector.model.BoundingBox;
import org.cassandragargoyle.portunix.tabledetector.model.TableCell;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleBiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PdfTableDetector on a synthetic PDF with empty edge cells
 *
 * @author Zdenek
 * @since 2026-10-25
 */
class PdfTableDetectorTest
{
	private static final float DELTA = 0.01f;

	private static final float FONT_SIZE = 10f;

	// Left edge of each column in PDF points
	private static final float[] COLUMN_X = {100f, 250f, 400f};

	// Baseline of the first row from the bottom of the page and distance between rows in PDF points
	private static final float Y_TOP = 750f;

	private static final float ROW_STEP = 20f;

	// Seeds per noise variant, a single seed passes by chance
	private static final int NOISE_SEEDS = 20;

	private static final PDRectangle CROP_BOX = new PDRectangle(50, 50, 500, 740);

	// Whitespace glyphs are drawn at the left page edge, like invisible spaces in real invoices
	private static final float WHITESPACE_X = 2f;

	// Synthetic table, empty strings are empty cells, blank strings are whitespace glyphs
	private static final String[][] ROWS = {
		{"Name", "Qty", "Price"},
		{"Apple", "3", "1.20"},
		{" ", "5", "2.50"},
		{"Cherry", "7", ""},
		{"Date", "9", "4.10"},
		{"", "11", ""},
		{"Elder", "13", "6.00"},
		{" ", "", ""},
	};

	// Row that also gets a no-break space glyph at the left page edge
	private static final int NBSP_ROW = 6;

	private static float pageWidth;

	private static float pageHeight;

	private static TableModel table;

	@BeforeAll
	static void detect() throws IOException
	{
		try (PDDocument document = createSyntheticPdf(PDRectangle.A4, 0, 0f, Y_TOP))
		{
			pageWidth = document.getPage(0).getMediaBox().getWidth();
			pageHeight = document.getPage(0).getMediaBox().getHeight();
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			table = tables.get(0);
		}
	}

	private static boolean isWhitespace(String text)
	{
		return text.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
	}

	static PDDocument createSyntheticPdf(PDRectangle mediaBox, int rotation, float xOffset, float yTop)	throws IOException
	{
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(mediaBox);
		page.setRotation(rotation);
		document.addPage(page);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

		try (PDPageContentStream stream = new PDPageContentStream(document, page))
		{
			float y = yTop;
			for (int rowIndex = 0; rowIndex < ROWS.length; rowIndex++)
			{
				String[] row = ROWS[rowIndex];
				if (rowIndex == NBSP_ROW)
				{
					stream.beginText();
					stream.setFont(font, FONT_SIZE);
					stream.newLineAtOffset(xOffset + WHITESPACE_X, y);
					stream.showText("\u00A0");
					stream.endText();
				}
				for (int col = 0; col < row.length; col++)
				{
					if (row[col].isEmpty())
					{
						continue;
					}
					stream.beginText();
					stream.setFont(font, FONT_SIZE);
					float x = isWhitespace(row[col]) ? WHITESPACE_X : COLUMN_X[col];
					stream.newLineAtOffset(xOffset + x, y);
					stream.showText(row[col]);
					stream.endText();
				}
				y -= ROW_STEP;
			}
		}
		return document;
	}

	private static float right(BoundingBox bbox)
	{
		return bbox.getX() + bbox.getWidth();
	}

	private static void assertInsidePage(BoundingBox bbox, String what)
	{
		assertTrue(Float.isFinite(bbox.getWidth()), what + " width not finite");
		assertTrue(bbox.getX() > 0, what + " x at sentinel 0: " + bbox.getX());
		assertTrue(bbox.getWidth() >= 0, what + " negative width");
		assertTrue(right(bbox) <= pageWidth, what + " right beyond page: " + right(bbox));
	}

	@Test
	void detectsGridWithEmptyEdgeCells()
	{
		assertEquals(ROWS.length, table.getRowCount());
		assertEquals(3, table.getColumnCount());
		assertEquals("", table.getCell(2, 0).getText());
		assertEquals("", table.getCell(3, 2).getText());
	}

	@Test
	void allBoundingBoxesInsidePage()
	{
		assertNotNull(table.getBoundingBox(), "table has no bounding box");
		assertInsidePage(table.getBoundingBox(), "table");
		for (TableCell cell : table.getCells())
		{
			if (cell.getBoundingBox() != null)
			{
				assertInsidePage(cell.getBoundingBox(), "cell " + cell.getRow() + "," + cell.getColumn());
			}
		}
	}

	@Test
	void emptyEdgeCellsLimitedByTableExtent()
	{
		BoundingBox tableBox = table.getBoundingBox();

		BoundingBox firstEmpty = table.getCell(2, 0).getBoundingBox();
		assertEquals(tableBox.getX(), firstEmpty.getX(), DELTA);
		assertTrue(right(firstEmpty) < COLUMN_X[1], "empty first cell reaches into second column");

		BoundingBox lastEmpty = table.getCell(3, 2).getBoundingBox();
		assertEquals(right(tableBox), right(lastEmpty), DELTA);
		assertTrue(lastEmpty.getX() > right(table.getCell(3, 1).getBoundingBox()),
			"empty last cell starts inside middle column");
	}

	@Test
	void whitespaceGlyphsIgnoredForGeometry()
	{
		BoundingBox tableBox = table.getBoundingBox();
		assertEquals(COLUMN_X[0], tableBox.getX(), 0.5f, "whitespace glyph spread the table");

		// Empty cell holding a whitespace glyph is limited like any other empty cell
		BoundingBox whitespaceCell = table.getCell(2, 0).getBoundingBox();
		assertNotNull(whitespaceCell);
		assertEquals(tableBox.getX(), whitespaceCell.getX(), DELTA);

		// No-break space is not covered by Character.isWhitespace, it must not widen the text cell
		BoundingBox nbspCell = table.getCell(NBSP_ROW, 0).getBoundingBox();
		assertNotNull(nbspCell);
		assertEquals(COLUMN_X[0], nbspCell.getX(), 0.5f);

		// Row with whitespace glyphs only has nothing visible to bound
		int lastRow = ROWS.length - 1;
		for (int col = 0; col < COLUMN_X.length; col++)
		{
			assertNull(table.getCell(lastRow, col).getBoundingBox(), "whitespace row cell " + col);
		}
	}

	@Test
	void tableBoxEqualsUnionOfTextCells()
	{
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (TableCell cell : table.getCells())
		{
			if (cell.getText().isEmpty())
			{
				continue;
			}
			BoundingBox bbox = cell.getBoundingBox();
			minX = Math.min(minX, bbox.getX());
			minY = Math.min(minY, bbox.getY());
			maxX = Math.max(maxX, right(bbox));
			maxY = Math.max(maxY, bbox.getY() + bbox.getHeight());
		}

		BoundingBox tableBox = table.getBoundingBox();
		assertEquals(minX, tableBox.getX(), DELTA);
		assertEquals(minY, tableBox.getY(), DELTA);
		assertEquals(maxX, right(tableBox), DELTA);
		assertEquals(maxY, tableBox.getY() + tableBox.getHeight(), DELTA);
	}

	@Test
	void textCellsKeepTextExtent()
	{
		BoundingBox apple = table.getCell(1, 0).getBoundingBox();
		assertEquals(COLUMN_X[0], apple.getX(), 0.5f);
		assertFalse(apple.getWidth() <= 0, "text cell has no width");
		assertTrue(apple.getWidth() < COLUMN_X[1] - COLUMN_X[0], "text cell wider than column");
	}

	@Test
	void textCellBoxesCoverGlyphsAboveBaseline()
	{
		for (TableCell cell : table.getCells())
		{
			if (cell.getText().isEmpty())
			{
				continue;
			}
			// Baseline measured from the top of the page, the coordinate system of BoundingBox
			float baseline = pageHeight - (Y_TOP - cell.getRow() * ROW_STEP);
			BoundingBox bbox = cell.getBoundingBox();
			String what = "cell " + cell.getRow() + "," + cell.getColumn();
			assertTrue(bbox.getHeight() > 0, what + " has no height");
			assertTrue(bbox.getY() < baseline, what + " top " + bbox.getY() + " not above baseline " + baseline);
			assertEquals(baseline, bbox.getBottom(), DELTA, what + " bottom not at baseline");
		}
	}

	// Text drawn at a column with its own font size and baseline shift in PDF points
	private record Text(String value, float fontSize, float shift)
	{
	}

	// Table with ROWS of 10 pt text and one mixed row at index 1
	private static TableModel detectMixedRow(Text... mixedRow) throws IOException
	{
		try (PDDocument document = new PDDocument())
		{
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			try (PDPageContentStream stream = new PDPageContentStream(document, page))
			{
				for (int rowIndex = 0; rowIndex < 4; rowIndex++)
				{
					for (int col = 0; col < COLUMN_X.length; col++)
					{
						Text text = rowIndex == 1 ? mixedRow[col] : new Text(ROWS[rowIndex == 0 ? 0 : 4][col], FONT_SIZE, 0f);
						stream.beginText();
						stream.setFont(font, text.fontSize());
						stream.newLineAtOffset(COLUMN_X[col], Y_TOP - rowIndex * ROW_STEP + text.shift());
						stream.showText(text.value());
						stream.endText();
					}
				}
			}
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			return tables.get(0);
		}
	}

	@Test
	void mixedFontSizesGiveEachCellItsOwnHeight() throws IOException
	{
		TableModel model = detectMixedRow(new Text("Pear", 6f, 0f), new Text("BIG", 20f, 0f), new Text("9.99", 10f, 0f));
		float baseline = PDRectangle.A4.getHeight() - (Y_TOP - ROW_STEP);
		BoundingBox small = model.getCell(1, 0).getBoundingBox();
		BoundingBox big = model.getCell(1, 1).getBoundingBox();
		BoundingBox normal = model.getCell(1, 2).getBoundingBox();

		// Glyph height scales with the font size, the first glyph of the row must not set it for all cells
		assertEquals(20f / 6f, big.getHeight() / small.getHeight(), 0.05f);
		assertEquals(2f, big.getHeight() / normal.getHeight(), 0.05f);
		assertTrue(big.getY() < normal.getY() && normal.getY() < small.getY(), "taller text must start higher");
		for (BoundingBox bbox : List.of(small, big, normal))
		{
			assertEquals(baseline, bbox.getBottom(), DELTA);
		}
	}

	@Test
	void shiftedBaselineKeepsCellsOnTheirOwnBaseline() throws IOException
	{
		// Superscript-like cell 2 pt above the row baseline, still grouped into the same row
		TableModel model = detectMixedRow(new Text("Plum", FONT_SIZE, 0f), new Text("5", FONT_SIZE, 2f), new Text("2.00", FONT_SIZE, 0f));
		float baseline = PDRectangle.A4.getHeight() - (Y_TOP - ROW_STEP);
		assertEquals(baseline, model.getCell(1, 0).getBoundingBox().getBottom(), DELTA);
		assertEquals(baseline - 2f, model.getCell(1, 1).getBoundingBox().getBottom(), DELTA);
		assertEquals(baseline, model.getCell(1, 2).getBoundingBox().getBottom(), DELTA);
	}

	@Test
	void emptyCellSpansVerticalExtentOfRow()
	{
		// Row 5 has text in the middle column only, the empty cells take its vertical extent
		BoundingBox text = table.getCell(5, 1).getBoundingBox();
		for (int col : new int[] {0, 2})
		{
			BoundingBox empty = table.getCell(5, col).getBoundingBox();
			assertEquals(text.getY(), empty.getY(), DELTA);
			assertEquals(text.getBottom(), empty.getBottom(), DELTA);
		}
	}

	// Table of 10 pt text at COLUMN_X, "" is an empty cell, a "^" draws the rest 6 pt, 12 pt right of the start
	private static TableModel detectRows(String[][] rows) throws IOException
	{
		return detectRows(COLUMN_X, rows);
	}

	// A negative column x is the right edge of a right-aligned column
	private static TableModel detectRows(float[] columnX, String[][] rows) throws IOException
	{
		try (PDDocument document = new PDDocument())
		{
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			try (PDPageContentStream stream = new PDPageContentStream(document, page))
			{
				for (int rowIndex = 0; rowIndex < rows.length; rowIndex++)
				{
					float y = Y_TOP - rowIndex * ROW_STEP;
					for (int col = 0; col < rows[rowIndex].length; col++)
					{
						if (rows[rowIndex][col].isEmpty())
						{
							continue;
						}
						String[] parts = rows[rowIndex][col].split("\\^");
						stream.beginText();
						stream.setFont(font, FONT_SIZE);
						float x = columnX[col] >= 0
							? columnX[col]
							: -columnX[col] - font.getStringWidth(parts[0]) / 1000f * FONT_SIZE;
						stream.newLineAtOffset(x, y);
						stream.showText(parts[0]);
						stream.endText();
						if (parts.length > 1)
						{
							stream.beginText();
							stream.setFont(font, 6f);
							stream.newLineAtOffset(columnX[col] + 12f, y);
							stream.showText(parts[1]);
							stream.endText();
						}
					}
				}
			}
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			return tables.get(0);
		}
	}

	private static void assertNoColumnEmptyInAllRows(TableModel model)
	{
		for (int col = 0; col < model.getColumnCount(); col++)
		{
			boolean empty = true;
			for (int row = 0; row < model.getRowCount(); row++)
			{
				empty &= model.getCell(row, col).getText().isEmpty();
			}
			assertFalse(empty, "column " + col + " is empty in all rows");
		}
	}

	@Test
	void varyingCellWidthsGiveOneBoundaryPerColumnGap() throws IOException
	{
		// Gap midpoints after "Qty"/"10kg" and after "3" are more than COLUMN_TOLERANCE apart (issue #178)
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Kiwi", "10^kg", "3.00"},
			{"Apple", "3", "1.20"},
			{"Apple", "3", "1.20"},
		});
		assertEquals(4, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("10kg", model.getCell(1, 1).getText());
		assertEquals("3.00", model.getCell(1, 2).getText());
	}

	@Test
	void alternatingNarrowAndWideValuesGiveOneBoundary() throws IOException
	{
		TableModel model = detectRows(new String[][] {
			{"Name", "Amount", "Unit"},
			{"Apple", "3", "kg"},
			{"Pear", "1,234.50", "kg"},
			{"Plum", "3", "pc"},
			{"Kiwi", "1,234.50", "pc"},
		});
		assertEquals(3, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("1,234.50", model.getCell(2, 1).getText());
		assertEquals("pc", model.getCell(4, 2).getText());
	}

	@Test
	void sparseRealColumnIsNotMerged() throws IOException
	{
		// Rows without the middle value have one wide gap, its midpoint must not swallow the real column
		TableModel model = detectRows(new String[][] {
			{"Name", "", "Price"},
			{"Apple", "X", "1.20"},
			{"Pear", "", "2.20"},
			{"Plum", "X", "3.20"},
			{"Fig", "", "4.20"},
			{"Kiwi", "", "5.20"},
		});
		assertEquals(3, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("X", model.getCell(1, 1).getText());
		assertEquals("X", model.getCell(3, 1).getText());
		assertEquals("2.20", model.getCell(2, 2).getText());
	}

	@Test
	void emptyMiddleCellsDoNotAddColumn() throws IOException
	{
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "", "1.20"},
			{"Pear", "12", "2.20"},
			{"Plum", "", "3.20"},
			{"Fig", "3", "4.20"},
		});
		assertEquals(3, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("", model.getCell(1, 1).getText());
		assertEquals("12", model.getCell(2, 1).getText());
		assertEquals("4.20", model.getCell(4, 2).getText());
	}

	@Test
	void wideGapAcrossEmptyMiddleCellDoesNotSplitColumn() throws IOException
	{
		// Midpoint of the gap "Apple" -> "1.20" lies left of the "3" in "1,234.50" (issue #180)
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "", "1.20"},
			{"Pear", "1,234.50", "2.20"},
			{"Plum", "", "3.20"},
			{"Fig", "3", "4.20"},
		});
		assertEquals(5, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("1,234.50", model.getCell(2, 1).getText());
		assertEquals("2.20", model.getCell(2, 2).getText());
		assertEquals("", model.getCell(1, 1).getText());
		assertEquals("1.20", model.getCell(1, 2).getText());
		assertEquals("", model.getCell(3, 1).getText());
		assertEquals("3", model.getCell(4, 1).getText());
	}

	@Test
	void singleFullRowWithEmptyMiddleCellsIsDetected() throws IOException
	{
		// Only the header fills all columns, the wide data row gaps vote for its column gaps (issue #182)
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "", "1.20"},
			{"Pear", "", "2.20"},
			{"Plum", "", "3.20"},
			{"Fig", "", "4.20"},
		});
		assertEquals(5, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertEquals("Qty", model.getCell(0, 1).getText());
		assertEquals("Price", model.getCell(0, 2).getText());
		for (int row = 1; row < 5; row++)
		{
			assertEquals("", model.getCell(row, 1).getText());
		}
		assertEquals("Apple", model.getCell(1, 0).getText());
		assertEquals("4.20", model.getCell(4, 2).getText());
	}

	@Test
	void headerWithTwoDataRowsAndEmptyMiddleCellsIsDetected() throws IOException
	{
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "", "1.20"},
			{"Pear", "", "2.20"},
		});
		assertEquals(3, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertEquals("Qty", model.getCell(0, 1).getText());
		assertEquals("", model.getCell(1, 1).getText());
		assertEquals("", model.getCell(2, 1).getText());
		assertEquals("2.20", model.getCell(2, 2).getText());
	}

	@Test
	void headerWithOneDataRowAndEmptyMiddleCellIsDetected() throws IOException
	{
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "", "1.20"},
		});
		assertEquals(2, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertEquals("Qty", model.getCell(0, 1).getText());
		assertEquals("", model.getCell(1, 1).getText());
		assertEquals("1.20", model.getCell(1, 2).getText());
	}

	@Test
	void singleFullFooterRowWithEmptyMiddleCellsIsDetected() throws IOException
	{
		TableModel model = detectRows(new String[][] {
			{"Apple", "", "1.20"},
			{"Pear", "", "2.20"},
			{"Plum", "", "3.20"},
			{"Total", "3", "6.60"},
		});
		assertEquals(4, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertEquals("", model.getCell(0, 1).getText());
		assertEquals("Total", model.getCell(3, 0).getText());
		assertEquals("3", model.getCell(3, 1).getText());
	}

	@Test
	void wideGapsAcrossTwoEmptyCellsKeepFourColumns() throws IOException
	{
		TableModel model = detectRows(new float[] {100f, 200f, 300f, 400f}, new String[][] {
			{"Name", "Qty", "Unit", "Price"},
			{"Apple", "", "", "1.20"},
			{"Pear", "", "", "2.20"},
			{"Plum", "", "", "3.20"},
		});
		assertEquals(4, model.getColumnCount());
		assertEquals("Unit", model.getCell(0, 2).getText());
		assertEquals("", model.getCell(1, 1).getText());
		assertEquals("", model.getCell(1, 2).getText());
		assertEquals("1.20", model.getCell(1, 3).getText());
	}

	@Test
	void columnGapSeenOnlyInHeaderIsConfirmedByWideGaps() throws IOException
	{
		// Every data row skips a different column, the Qty | Unit gap appears only in the header
		TableModel model = detectRows(new float[] {100f, 200f, 300f, 400f}, new String[][] {
			{"Name", "Qty", "Unit", "Price"},
			{"Apple", "", "kg", "1.20"},
			{"Pear", "2", "", "2.20"},
			{"Plum", "", "", "3.20"},
		});
		assertEquals(4, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("Qty", model.getCell(0, 1).getText());
		assertEquals("Unit", model.getCell(0, 2).getText());
		assertEquals("kg", model.getCell(1, 2).getText());
		assertEquals("2", model.getCell(2, 1).getText());
		assertEquals("", model.getCell(2, 2).getText());
	}

	@Test
	void fiveColumnsWithStaggeredEmptyCellsKeepAllColumns() throws IOException
	{
		TableModel model = detectRows(new float[] {60f, 160f, 260f, 360f, 460f}, new String[][] {
			{"Name", "Qty", "Unit", "Tax", "Price"},
			{"Apple", "", "kg", "", "1.20"},
			{"Pear", "2", "", "21%", "2.20"},
			{"Plum", "", "", "", "3.20"},
			{"Fig", "", "pc", "", "4.20"},
		});
		assertEquals(5, model.getColumnCount());
		assertNoColumnEmptyInAllRows(model);
		assertEquals("Tax", model.getCell(0, 3).getText());
		assertEquals("kg", model.getCell(1, 2).getText());
		assertEquals("21%", model.getCell(2, 3).getText());
		assertEquals("", model.getCell(3, 1).getText());
		assertEquals("pc", model.getCell(4, 2).getText());
	}

	@Test
	void rightAlignedNumbersWithEmptyMiddleCellsAreDetected() throws IOException
	{
		TableModel model = detectRows(new float[] {100f, -300f, -450f}, new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "", "1.20"},
			{"Pear", "", "12.20"},
			{"Plum", "", "123.20"},
		});
		assertEquals(4, model.getRowCount());
		assertEquals(3, model.getColumnCount());
		assertEquals("Qty", model.getCell(0, 1).getText());
		assertEquals("", model.getCell(2, 1).getText());
		assertEquals("123.20", model.getCell(3, 2).getText());
	}

	@Test
	void voteOnlyBoundaryThatCutsTextIsRejected() throws IOException
	{
		// Known limitation: the boundary voted for lies at the header gap midpoint, which the long
		// value crosses, so the Name and Qty columns stay merged instead of splitting the value (issue #182)
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Blackcurrant jam jar", "", "1.20"},
			{"Pear", "", "2.20"},
			{"Plum", "", "3.20"},
		});
		assertEquals(2, model.getColumnCount());
		assertEquals("Blackcurrant jam jar", model.getCell(1, 0).getText());
		assertEquals("1.20", model.getCell(1, 1).getText());
	}

	private static TableModel detectSingle(PDRectangle mediaBox, int rotation, float xOffset, float yTop)
		throws IOException
	{
		try (PDDocument document = createSyntheticPdf(mediaBox, rotation, xOffset, yTop))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			return tables.get(0);
		}
	}

	private static void assertFiniteWithin(TableModel model, float limit)
	{
		assertNotNull(model.getBoundingBox(), "table has no bounding box");
		for (TableCell cell : model.getCells())
		{
			BoundingBox bbox = cell.getBoundingBox();
			if (bbox != null)
			{
				String what = "cell " + cell.getRow() + "," + cell.getColumn();
				assertTrue(Float.isFinite(bbox.getWidth()), what + " width not finite");
				assertTrue(bbox.getX() >= 0 && right(bbox) <= limit, what + " outside page: " + bbox.getX() + ".." + right(bbox));
			}
		}
	}

	@Test
	void rotatedPageKeepsBoundingBoxesFinite() throws IOException
	{
		PDRectangle a4 = PDRectangle.A4;
		TableModel rotated = detectSingle(a4, 90, 0f, 750f);
		assertFiniteWithin(rotated, Math.max(a4.getWidth(), a4.getHeight()));
	}

	private static void assertSameGrid(TableModel expected, TableModel actual, String what)
	{
		assertEquals(expected.getRowCount(), actual.getRowCount(), what + " row count");
		assertEquals(expected.getColumnCount(), actual.getColumnCount(), what + " column count");
		for (TableCell cell : expected.getCells())
		{
			assertEquals(cell.getText(), actual.getCell(cell.getRow(), cell.getColumn()).getText(),
				what + " cell " + cell.getRow() + "," + cell.getColumn());
		}
	}

	@Test
	void rotatedPageGivesSameGridAsUnrotatedPage() throws IOException
	{
		for (int rotation : new int[] {90, 180, 270})
		{
			assertSameGrid(table, detectSingle(PDRectangle.A4, rotation, 0f, Y_TOP), "rotation " + rotation);
		}
	}

	private static boolean contains(BoundingBox bbox, float x, float y)
	{
		float tolerance = 0.5f;
		return x >= bbox.getX() - tolerance && x <= right(bbox) + tolerance
			&& y >= bbox.getY() - tolerance && y <= bbox.getBottom() + tolerance;
	}

	/**
	 * Every text cell box covers its first visible glyph and every visible glyph lies in a table box,
	 * glyph origins as PDFBox reports them in the displayed page without direction adjustment
	 */
	private static void assertBoxesCoverDisplayedText(PDDocument document, List<TableModel> tables, String what)
		throws IOException
	{
		List<TextPosition> glyphs = new ArrayList<>();
		PDFTextStripper stripper = new PDFTextStripper()
		{
			@Override
			protected void writeString(String text, List<TextPosition> positions)
			{
				glyphs.addAll(positions);
			}
		};
		stripper.getText(document);

		// Displayed page of a quarter turn is the crop box with swapped sides
		PDPage page = document.getPage(0);
		boolean swapped = Math.floorMod(page.getRotation(), 180) == 90;
		float displayWidth = swapped ? page.getCropBox().getHeight() : page.getCropBox().getWidth();
		float displayHeight = swapped ? page.getCropBox().getWidth() : page.getCropBox().getHeight();

		for (TableModel model : tables)
		{
			BoundingBox tableBox = model.getBoundingBox();
			assertTrue(tableBox.getX() >= 0 && right(tableBox) <= displayWidth, what + " " + model.getId() + " outside page width");
			assertTrue(tableBox.getY() >= 0 && tableBox.getBottom() <= displayHeight, what + " " + model.getId() + " outside page height");

			for (TableCell cell : model.getCells())
			{
				// First visible glyph, the text of cell 6,0 starts with a no-break space
				String first = cell.getText().codePoints()
					.filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))
					.limit(1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
				if (first.isEmpty())
				{
					continue;
				}
				boolean covered = false;
				for (TextPosition glyph : glyphs)
				{
					covered |= glyph.getUnicode().equals(first)
						&& contains(cell.getBoundingBox(), glyph.getX(), glyph.getY());
				}
				assertTrue(covered, what + " " + model.getId() + " cell " + cell.getRow() + "," + cell.getColumn() + " misses its text");
			}
		}
		for (TextPosition glyph : glyphs)
		{
			if (!isWhitespace(glyph.getUnicode()))
			{
				boolean inTable = false;
				for (TableModel model : tables)
				{
					inTable |= contains(model.getBoundingBox(), glyph.getX(), glyph.getY());
				}
				assertTrue(inTable, what + " glyph " + glyph.getUnicode() + " outside every table box");
			}
		}
	}

	@Test
	void rotatedPageBoxesCoverDisplayedText() throws IOException
	{
		for (int rotation : new int[] {90, 180, 270})
		{
			try (PDDocument document = createSyntheticPdf(PDRectangle.A4, rotation, 0f, Y_TOP))
			{
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(1, tables.size(), "one table expected");
				assertBoxesCoverDisplayedText(document, tables, "rotation " + rotation);
			}
		}
	}

	@Test
	void cropBoxSmallerThanMediaBoxOnRotatedPage() throws IOException
	{
		// PDFBox reports positions relative to the crop box, the displayed page is the turned crop box
		// The content is shifted by 50 pt, so the whitespace glyphs at the page edge stay inside the crop box
		for (int rotation : new int[] {0, 90, 270})
		{
			try (PDDocument document = createSyntheticPdf(PDRectangle.A4, rotation, 50f, Y_TOP))
			{
				document.getPage(0).setCropBox(new PDRectangle(40, 50, 500, 740));
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				String what = "crop box, rotation " + rotation;
				assertEquals(1, tables.size(), what + " one table expected");
				assertSameGrid(table, tables.get(0), what);
				assertBoxesCoverDisplayedText(document, tables, what);
			}
		}
	}

	@Test
	void textOutsideCropBoxIsIgnored() throws IOException
	{
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			// The first column lies left of the crop box, viewers do not show it
			try (PDDocument document = createColumnsPdf(rotation, CROP_BOX, 700f, 5, new float[] {10f, 200f, 400f},
				"Off", "Mid", "End"))
			{
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				String what = "rotation " + rotation;
				assertEquals(1, tables.size(), what + " one table expected");
				TableModel model = tables.get(0);
				assertEquals(5, model.getRowCount(), what + " rows");
				assertEquals(2, model.getColumnCount(), what + " columns");
				assertEquals("Mid0", model.getCell(0, 0).getText(), what);
				assertEquals("End4", model.getCell(4, 1).getText(), what);
			}
		}
	}

	@Test
	void partlyVisibleGlyphsAreKept() throws IOException
	{
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			String what = "rotation " + rotation;

			// The first glyph starts 1 pt left of the crop box and stays mostly visible
			try (PDDocument document = createColumnsPdf(rotation, CROP_BOX, 700f, 6, new float[] {49f, 200f, 400f},
				"Xalpha", "Mid", "End"))
			{
				TableModel model = new PdfTableDetector().detectTables(document, 1).get(0);
				assertEquals(3, model.getColumnCount(), what);
				assertEquals("Xalpha0", model.getCell(0, 0).getText(), what);
			}

			// The last baseline lies 2 pt under the crop box, the upper part of the letters is visible
			try (PDDocument document = createColumnsPdf(rotation, CROP_BOX, 148f, 6, new float[] {100f, 250f, 400f},
				"Aaa", "Bbb", "Ccc"))
			{
				TableModel model = new PdfTableDetector().detectTables(document, 1).get(0);
				assertEquals(6, model.getRowCount(), what);
				assertEquals("Aaa5", model.getCell(5, 0).getText(), what);
			}
		}
	}

	@Test
	void cellCrossingCropBoxEdgeHoldsVisibleText() throws IOException
	{
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			try (PDDocument document = createColumnsPdf(rotation, CROP_BOX, 700f, 6, new float[] {100f, 300f, 530f},
				"Aaa", "Mid", "Truncated"))
			{
				TableModel model = new PdfTableDetector().detectTables(document, 1).get(0);
				assertEquals("Trun", model.getCell(0, 2).getText(), "rotation " + rotation);
			}
		}
	}

	@Test
	void emptyCropBoxFallsBackToMediaBox() throws IOException
	{
		PDRectangle[] cropBoxes = {new PDRectangle(0, 0, 0, 0), new PDRectangle(100, 100, 0, 300),
			new PDRectangle(100, 100, 300, 0), new PDRectangle(1000, 1000, 200, 200)};
		for (PDRectangle cropBox : cropBoxes)
		{
			for (int rotation : new int[] {0, 90, 180, 270})
			{
				String what = cropBox + " rotation " + rotation;
				try (PDDocument document = createColumnsPdf(rotation, cropBox, 700f, 6, new float[] {100f, 250f, 400f},
					"Aaa", "Bbb", "Ccc"))
				{
					PDPage page = document.getPage(0);
					COSBase declared = page.getCOSObject().getItem(COSName.CROP_BOX);

					TableModel model = new PdfTableDetector().detectTables(document, 1).get(0);

					assertEquals(6, model.getRowCount(), what);
					assertEquals(3, model.getColumnCount(), what);
					assertEquals("Aaa0", model.getCell(0, 0).getText(), what);
					assertEquals("Ccc5", model.getCell(5, 2).getText(), what);
					assertTrue(model.getBoundingBox().getX() >= 0, what);
					assertTrue(model.getBoundingBox().getY() >= 0, what);
					assertSame(declared, page.getCOSObject().getItem(COSName.CROP_BOX), what);
				}
			}
		}
	}

	private static PDDocument createColumnsPdf(int rotation, PDRectangle cropBox, float topBaseline, int rowCount,
		float[] columnX, String... prefix) throws IOException
	{
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.A4);
		page.setRotation(rotation);
		page.setCropBox(cropBox);
		document.addPage(page);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try (PDPageContentStream stream = new PDPageContentStream(document, page))
		{
			for (int rowIndex = 0; rowIndex < rowCount; rowIndex++)
			{
				for (int col = 0; col < columnX.length; col++)
				{
					stream.beginText();
					stream.setFont(font, FONT_SIZE);
					stream.newLineAtOffset(columnX[col], topBaseline - rowIndex * ROW_STEP);
					stream.showText(prefix[col] + rowIndex);
					stream.endText();
				}
			}
		}
		return document;
	}

	/**
	 * Draw ROWS with the text matrix rotated by theta degrees around the first baseline (originX, originY)
	 * Whitespace cells are drawn in their column, the no-break space of NBSP_ROW is left out
	 */
	private static void drawRotatedRows(PDDocument document, PDPage page, float originX, float originY, int theta)
		throws IOException
	{
		drawRotatedRows(document, page, originX, originY, theta, (row, col) -> 0.0);
	}

	/**
	 * Same as above, every cell text is turned by a further angle in degrees per row and column, like the
	 * words of an OCR text layer, the cell origins stay on the grid
	 */
	private static void drawRotatedRows(PDDocument document, PDPage page, float originX, float originY, double theta,
		ToDoubleBiFunction<Integer, Integer> noise) throws IOException
	{
		double angle = Math.toRadians(theta);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false))
		{
			for (int rowIndex = 0; rowIndex < ROWS.length; rowIndex++)
			{
				for (int col = 0; col < COLUMN_X.length; col++)
				{
					if (ROWS[rowIndex][col].isEmpty())
					{
						continue;
					}
					// Cell origin in the table frame, turned by the angle around the table origin
					float localX = COLUMN_X[col] - COLUMN_X[0];
					float localY = -rowIndex * ROW_STEP;
					float x = (float) (originX + localX * Math.cos(angle) - localY * Math.sin(angle));
					float y = (float) (originY + localX * Math.sin(angle) + localY * Math.cos(angle));
					stream.beginText();
					stream.setFont(font, FONT_SIZE);
					double cellNoise = Math.toRadians(noise.applyAsDouble(rowIndex, col));
					stream.setTextMatrix(Matrix.getRotateInstance(angle + cellNoise, x, y));
					stream.showText(ROWS[rowIndex][col]);
					stream.endText();
				}
			}
		}
	}

	// Page with ROWS drawn once per placement: origin x, origin y and text angle in degrees
	private static PDDocument createRotatedRowsPdf(int rotation, float[]... placements) throws IOException
	{
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.A4);
		page.setRotation(rotation);
		document.addPage(page);
		for (float[] placement : placements)
		{
			drawRotatedRows(document, page, placement[0], placement[1], (int) placement[2]);
		}
		return document;
	}

	// Grid of ROWS drawn by drawRotatedRows on an unrotated page, the reference for rotated variants
	private static TableModel detectUprightRows() throws IOException
	{
		try (PDDocument document = createRotatedRowsPdf(0, new float[] {COLUMN_X[0], Y_TOP, 0}))
		{
			return new PdfTableDetector().detectTables(document, 1).get(0);
		}
	}

	@Test
	void textTurnedAgainstPageRotationIsUpright() throws IOException
	{
		// Text turned by 270 degrees on a page with /Rotate 90 is displayed upright
		try (PDDocument document = createRotatedRowsPdf(90, new float[] {150f, Y_TOP, 270}))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			assertSameGrid(detectUprightRows(), tables.get(0), "text 270 on rotation 90");
			assertBoxesCoverDisplayedText(document, tables, "text 270 on rotation 90");

			// Upright table: rows stack downward, so the table is wider than high
			BoundingBox box = tables.get(0).getBoundingBox();
			assertTrue(box.getWidth() > box.getHeight(), "table not upright: " + box.getWidth() + " x " + box.getHeight());
		}
	}

	// Page with ROWS at the angle, the table centre stays in the page centre so that no cell leaves the page
	private static PDDocument createCenteredRowsPdf(int rotation, double theta) throws IOException
	{
		return createCenteredRowsPdf(rotation, theta, (row, col) -> 0.0);
	}

	private static PDDocument createCenteredRowsPdf(int rotation, double theta, ToDoubleBiFunction<Integer, Integer> noise)
		throws IOException
	{
		double angle = Math.toRadians(theta);
		float localX = (COLUMN_X[2] - COLUMN_X[0]) / 2;
		float localY = -(ROWS.length - 1) * ROW_STEP / 2;
		float originX = (float) (PDRectangle.A4.getWidth() / 2 - localX * Math.cos(angle) + localY * Math.sin(angle));
		float originY = (float) (PDRectangle.A4.getHeight() / 2 - localX * Math.sin(angle) - localY * Math.cos(angle));
		PDDocument document = createRotatedRowsPdf(rotation);
		drawRotatedRows(document, document.getPage(0), originX, originY, theta, noise);
		return document;
	}

	@Test
	void skewedTableGivesSameGridAsUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			for (int theta : new int[] {30, 2, -2, 178, 120})
			{
				String what = "angle " + theta + " rotation " + rotation;
				try (PDDocument document = createCenteredRowsPdf(rotation, theta))
				{
					List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
					assertEquals(1, tables.size(), what + " one table expected");
					assertSameGrid(upright, tables.get(0), what);
					assertBoxesCoverDisplayedText(document, tables, what);
				}
			}
		}
	}

	@Test
	void angleNoiseOfCellsKeepsTableTogether() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			for (int theta : new int[] {0, 2, -2, 30, 90})
			{
				for (double noise : new double[] {0.1, 0.3, 0.5, 1.0})
				{
					for (long seed = 1; seed <= NOISE_SEEDS; seed++)
					{
						String what = "rotation " + rotation + " angle " + theta + " noise " + noise + " seed " + seed;
						Random random = new Random(seed);
						try (PDDocument document = createCenteredRowsPdf(rotation, theta, (row, col) -> uniform(random, noise)))
						{
							List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
							assertEquals(1, tables.size(), what + " one table expected");
							assertSameGrid(upright, tables.get(0), what);
							assertBoxesCoverDisplayedText(document, tables, what);
						}
					}
				}
			}
		}
	}

	private static double uniform(Random random, double noise)
	{
		return (random.nextDouble() * 2 - 1) * noise;
	}

	private static void assertUprightTable(TableModel upright, ToDoubleBiFunction<Integer, Integer> noise, String what)
		throws IOException
	{
		try (PDDocument document = createCenteredRowsPdf(0, 0, noise))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), what + " one table expected");
			assertSameGrid(upright, tables.get(0), what);
		}
	}

	@Test
	void partlyExactCellsKeepNearlyUprightTableTogether() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double exactShare : new double[] {0.25, 0.5, 0.75})
		{
			for (double noise : new double[] {0.3, 0.5, 1.0})
			{
				for (long seed = 1; seed <= NOISE_SEEDS; seed++)
				{
					Random random = new Random(seed);
					assertUprightTable(upright, (row, col) -> random.nextDouble() < exactShare ? 0 : uniform(random, noise),
						"exact share " + exactShare + " noise " + noise + " seed " + seed);
				}
			}
		}
	}

	@Test
	void quantizedAnglesKeepNearlyUprightTableTogether() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double step : new double[] {0.1, 0.2, 0.25, 0.5})
		{
			for (long seed = 1; seed <= NOISE_SEEDS; seed++)
			{
				Random random = new Random(seed);
				assertUprightTable(upright, (row, col) -> (random.nextInt(5) - 2) * step, "step " + step + " seed " + seed);
			}
		}
	}

	@Test
	void angleNoiseOfRowsKeepsNearlyUprightTableTogether() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (long seed = 1; seed <= NOISE_SEEDS; seed++)
		{
			Random random = new Random(seed);
			double[] rowNoise = random.doubles(ROWS.length, -1, 1).toArray();
			assertUprightTable(upright, (row, col) -> rowNoise[row], "seed " + seed);
		}
	}

	// Lines of running text at the angle, the first baseline starts at (originX, originY)
	private static void drawParagraph(PDDocument document, PDPage page, float originX, float originY, double theta)
		throws IOException
	{
		drawParagraph(document, page, originX, originY, theta, () -> 0.0);
	}

	// Same as above, every line is turned by a further angle in degrees on its place in the paragraph
	private static void drawParagraph(PDDocument document, PDPage page, float originX, float originY, double theta,
		DoubleSupplier noise) throws IOException
	{
		double angle = Math.toRadians(theta);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false))
		{
			for (int line = 0; line < 12; line++)
			{
				float x = (float) (originX + line * 14 * Math.sin(angle));
				float y = (float) (originY - line * 14 * Math.cos(angle));
				stream.beginText();
				stream.setFont(font, FONT_SIZE);
				stream.setTextMatrix(Matrix.getRotateInstance(angle + Math.toRadians(noise.getAsDouble()), x, y));
				stream.showText("The quick brown fox jumps over the lazy dog again and again in this paragraph");
				stream.endText();
			}
		}
	}

	@Test
	void slightlySkewedParagraphDoesNotTurnUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double tableTheta : new double[] {0, 0.006, 0.01, 0.19, 0.21})
		{
			for (double theta : new double[] {0.5, 0.7, 0.9})
			{
				String what = "table at " + tableTheta + " paragraph at " + theta;
				try (PDDocument document = createRotatedRowsPdf(0))
				{
					drawRotatedRows(document, document.getPage(0), COLUMN_X[0], Y_TOP, tableTheta, (row, col) -> 0.0);
					drawParagraph(document, document.getPage(0), 40f, 400f, theta);
					List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
					assertEquals(1, tables.size(), what + " one table expected");
					assertSameGrid(upright, tables.get(0), what);
				}
			}
		}
	}

	@Test
	void slightlySkewedTableStaysApartFromUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		try (PDDocument document = createRotatedRowsPdf(0, new float[] {COLUMN_X[0], Y_TOP, 0}))
		{
			drawRotatedRows(document, document.getPage(0), COLUMN_X[0], 300f, 1.5, (row, col) -> 0.0);
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(2, tables.size(), "two tables expected");
			assertSameGrid(upright, tables.get(0), "upright table");
			assertSameGrid(upright, tables.get(1), "skewed table");
			assertBoxesCoverDisplayedText(document, tables, "upright and 1.5 degrees");
		}
	}

	@Test
	void smallExactSkewGivesSameGridAsUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			for (double theta : new double[] {0.8, -0.8, 1.0, -1.0, 1.05, 178.95})
			{
				String what = "angle " + theta + " rotation " + rotation;
				try (PDDocument document = createCenteredRowsPdf(rotation, theta))
				{
					List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
					assertEquals(1, tables.size(), what + " one table expected");
					assertSameGrid(upright, tables.get(0), what);
					assertBoxesCoverDisplayedText(document, tables, what);
				}
			}
		}
	}

	@Test
	void ocrAnglesOffPageSkewGiveSameGridAsUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int rotation : new int[] {0, 90, 180, 270})
		{
			for (double theta : new double[] {-3, 3, -1.5, 1})
			{
				// Most glyphs at 0 degrees, the last column at two thirds of the skew
				assertSkewedLayer(upright, rotation, theta, (row, col) -> col == 2 ? -theta / 3 : -theta, "two families");
				// Every glyph at 0 degrees, the angles carry no trace of the skew
				assertSkewedLayer(upright, rotation, theta, (row, col) -> -theta, "all upright");
			}
		}
	}

	private static void assertSkewedLayer(TableModel upright, int rotation, double theta,
		ToDoubleBiFunction<Integer, Integer> noise, String layer) throws IOException
	{
		String what = layer + " angle " + theta + " rotation " + rotation;
		try (PDDocument document = createCenteredRowsPdf(rotation, theta, noise))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), what + " one table expected");
			assertSameGrid(upright, tables.get(0), what);
		}
	}

	@Test
	void tablesAtDifferentAnglesStayApartOnOnePage() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double theta : new double[] {1.5, -1.5, 2, -2})
		{
			String what = "upright and " + theta;
			try (PDDocument document = createRotatedRowsPdf(0, new float[] {COLUMN_X[0], Y_TOP, 0}))
			{
				drawRotatedRows(document, document.getPage(0), COLUMN_X[0], 300f, theta, (row, col) -> 0.0);
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(2, tables.size(), what + " two tables expected");
				assertSameGrid(upright, tables.get(0), what + " first table");
				assertSameGrid(upright, tables.get(1), what + " second table");
			}
		}
	}

	@Test
	void longTurnedLineDoesNotBreakSkewedTable() throws IOException
	{
		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		TableModel upright = detectUprightRows();
		try (PDDocument document = createCenteredRowsPdf(0, 2))
		{
			try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0), PDPageContentStream.AppendMode.APPEND, false))
			{
				stream.beginText();
				stream.setFont(font, FONT_SIZE);
				stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(12), COLUMN_X[0], 420f));
				stream.showText("CONFIDENTIAL DRAFT DOCUMENT DO NOT COPY");
				stream.endText();
			}
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			assertSameGrid(upright, tables.get(0), "line over skewed table");
		}
	}

	@Test
	void angleNoiseOnUprightGridKeepsGridOfUprightPage() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int seed = 0; seed < NOISE_SEEDS; seed++)
		{
			Random random = new Random(seed);
			assertUprightTable(upright, (row, col) -> uniform(random, 1), "small cell angles seed " + seed);
		}
	}

	@Test
	void pageWithManyGlyphsFindsSkewFromPositions() throws IOException
	{
		// More glyphs than PAGE_SKEW_MAX_GLYPHS, written at 0 degrees on rows skewed by 2 degrees
		try (PDDocument document = createUprightWordsOnSkewedGrid(2, 100, 4, 120f, 7.5f, 6, (row, col) -> String.format("R%03dC%d", row, col)))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			assertEquals(100, tables.get(0).getRowCount(), "rows");
			assertEquals(4, tables.get(0).getColumnCount(), "columns");
			assertEquals("R042C3", tables.get(0).getCell(42, 3).getText().trim());
		}
	}

	@Test
	void longWordAtItsOwnAngleStaysInItsRow() throws IOException
	{
		// The end of the word lies more than ROW_TOLERANCE off the row in the frame of the page
		for (double theta : new double[] {-3, 3})
		{
			String what = "angle " + theta;
			try (PDDocument document = createUprightWordsOnSkewedGrid(theta, 6, 3, 170f, 26f, 10, (row, col) -> "Elderberryjam" + row + "works" + col))
			{
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(1, tables.size(), what + " one table expected");
				assertEquals(6, tables.get(0).getRowCount(), what + " rows");
				assertEquals(3, tables.get(0).getColumnCount(), what + " columns");
				assertEquals("Elderberryjam4works2", tables.get(0).getCell(4, 2).getText().trim(), what);
			}
		}
	}

	@Test
	void realOcrLayerOffPageSkewGivesItsTable() throws IOException
	{
		// Text-only Tesseract 5.5 output of a generated scan of an 8x4 table skewed by -3 degrees: 100 glyphs
		// at 0 degrees and 73 at -2.98 degrees, whose family alone finds a frame next to the skew of the page
		try (InputStream stream = PdfTableDetectorTest.class.getResourceAsStream("/ocr/skewed-3-degrees-two-angles.pdf");
			PDDocument document = Loader.loadPDF(stream.readAllBytes()))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			assertEquals(8, tables.get(0).getRowCount(), "rows");
			assertEquals(4, tables.get(0).getColumnCount(), "columns");
			assertEquals("Harbor", tables.get(0).getCell(6, 2).getText().trim());
			assertEquals("Elderberry", tables.get(0).getCell(5, 0).getText().trim());
		}
	}

	@Test
	void realOcrLayerWithOneAngleGivesTwoTables() throws IOException
	{
		// Text-only Tesseract 5.5 output of a generated scan of an upright and a 2 degrees table: every glyph
		// at 0 degrees, so only the positions of the two regions tell the tables apart
		try (InputStream stream = PdfTableDetectorTest.class.getResourceAsStream("/ocr/upright-and-2-degrees-one-angle.pdf");
			PDDocument document = Loader.loadPDF(stream.readAllBytes()))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(2, tables.size(), "two tables expected");
			for (TableModel table : tables)
			{
				assertEquals(8, table.getRowCount(), "rows");
				assertEquals(4, table.getColumnCount(), "columns");
			}
			assertEquals("Apple", tables.get(0).getCell(5, 2).getText().trim());
			assertEquals("Banana", tables.get(1).getCell(5, 2).getText().trim());
		}
	}

	@Test
	void realOcrLayerWithFamilyFrameNearPageSkewGivesTwoTables() throws IOException
	{
		// Text-only Tesseract 5.5 output (--psm 6) of a generated scan of tables at -1.5 and -3 degrees: the
		// frame of the family lies next to the skew of the page and gives way to it, so like the page skew
		// it must not set the bar for a region (issue #215)
		try (InputStream stream = PdfTableDetectorTest.class.getResourceAsStream("/ocr/two-skews-family-frame-near-page-skew.pdf");
			PDDocument document = Loader.loadPDF(stream.readAllBytes()))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(2, tables.size(), "two tables expected");
			for (TableModel table : tables)
			{
				assertEquals(8, table.getRowCount(), "rows");
				assertEquals(4, table.getColumnCount(), "columns");
			}
			assertEquals("Low5x2", tables.get(0).getCell(5, 2).getText().trim());
			assertEquals("Top5x2", tables.get(1).getCell(5, 2).getText().trim());
		}
	}

	@Test
	void tablesAtDifferentSkewWithOneGlyphAngleStayApart() throws IOException
	{
		for (double theta : new double[] {1.5, -1.5, 2, -2, 3, -3})
		{
			String what = "upright and " + theta;
			try (PDDocument document = createGridsPdf(
				new Grid(70, 790, 0, 0, 8, 4, 120, 18, "Top"),
				new Grid(70, 560, theta, 0, 8, 4, 120, 18, "Low")))
			{
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(2, tables.size(), what + " two tables expected");
				assertGrid(new Grid(70, 790, 0, 0, 8, 4, 120, 18, "Top"), tables.get(0), what);
				assertGrid(new Grid(70, 560, theta, 0, 8, 4, 120, 18, "Low"), tables.get(1), what);
			}
		}
	}

	@Test
	void smallUprightTableKeepsItsGridAboveLargeSkewedTable() throws IOException
	{
		for (double theta : new double[] {1.5, -1.5, 2, -2, 3, -3})
		{
			// All glyphs at 0 degrees or all at the skew of the large table
			for (double glyphAngle : new double[] {0, theta})
			{
				String what = "large table at " + theta + " glyphs at " + glyphAngle;
				Grid small = new Grid(70, 790, 0, 0, 5, 3, 130, 18, "Small");
				Grid large = new Grid(70, 610, theta, glyphAngle, 24, 4, 115, 18, "Large");
				try (PDDocument document = createGridsPdf(small, large))
				{
					List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
					assertEquals(2, tables.size(), what + " two tables expected");
					assertGrid(small, tables.get(0), what);
					assertGrid(large, tables.get(1), what);
				}
			}
		}
	}

	@Test
	void twoSkewedTablesWithOneGlyphAngleStayApart() throws IOException
	{
		// The frame of the whole page lies between the two tables and suits neither of them (issue #215)
		for (double[] thetas : new double[][] {{1.5, -2}, {-2, 1.5}, {2, 3}, {3, 2}})
		{
			for (double lowY : new double[] {560, 500})
			{
				assertTwoGrids(
					new Grid(70, 790, thetas[0], 0, 8, 4, 120, 18, "Top"),
					new Grid(70, lowY, thetas[1], 0, 8, 4, 120, 18, "Low"));
			}
		}
	}

	@Test
	void skewWithColumnsOnNeighbouringRowsStaysApart() throws IOException
	{
		// With a row step of 18 and a column step of 110 each column moves half a row at 4.8 degrees, the
		// rows of the skewed table pair up in the upright frame and fall to under half only in its own
		assertTwoGrids(
			new Grid(70, 790, 0, 0, 8, 4, 110, 18, "Top"),
			new Grid(70, 560, 4.8, 0, 8, 4, 110, 18, "Low"));
	}

	@Test
	void denseTablesAtDifferentSkewStayApart() throws IOException
	{
		// Dense rows run together in the frame of the whole page, which must not set the bar for a region
		for (double[] thetas : new double[][] {{0, 2}, {1.5, -2}, {-2, 1.5}, {2, -1.5}, {2, 3}, {-2, -3}})
		{
			assertTwoGrids(
				new Grid(40, 815, thetas[0], 0, 48, 8, 72, 6.5f, "T"),
				new Grid(40, 430, thetas[1], 0, 48, 8, 72, 6.5f, "L"));
		}
	}

	private static void assertTwoGrids(Grid first, Grid second) throws IOException
	{
		String what = first.theta() + " and " + second.theta();
		try (PDDocument document = createGridsPdf(first, second))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(2, tables.size(), what + " two tables expected");
			// Skewed tables follow by the angle of their frame
			boolean inOrder = first.cell(0, 0).equals(tables.get(0).getCell(0, 0).getText().trim());
			assertGrid(first, tables.get(inOrder ? 0 : 1), what);
			assertGrid(second, tables.get(inOrder ? 1 : 0), what);
		}
	}

	@Test
	void skewedTableSplitByWideBandStaysOneTable() throws IOException
	{
		for (double theta : new double[] {2, -2, 3})
		{
			// The second part of the rows continues the first one on the same skewed grid, one row step
			// plus a band of 110 points further
			double angle = Math.toRadians(theta);
			Grid first = new Grid(70, 780, 0, 0, 6, 4, 120, 18, "Row");
			double drop = 6 * 18 + 110;
			try (PDDocument document = createGridsPdf(
				new Grid(70, 780, theta, 0, 6, 4, 120, 18, "Row"),
				new Grid(70 + drop * Math.sin(angle), 780 - drop * Math.cos(angle), theta, 0, 6, 4, 120, 18, "End")))
			{
				String what = "angle " + theta;
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(1, tables.size(), what + " one table expected");
				assertEquals(2 * first.rows(), tables.get(0).getRowCount(), what + " rows");
				assertEquals(first.columns(), tables.get(0).getColumnCount(), what + " columns");
				assertEquals("End5x3", tables.get(0).getCell(11, 3).getText().trim(), what);
			}
		}
	}

	@Test
	void twoColumnsOnOffsetBaselinesTakeNoFrame() throws IOException
	{
		// The rows of two columns halve at the angle of the offset, that is no skew, the two blocks slope in
		// opposite directions, so the page gives no skew either
		try (PDDocument document = new PDDocument())
		{
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page))
			{
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
				drawOffsetBlock(stream, 760, new float[] {0, 7});
				drawOffsetBlock(stream, 520, new float[] {7, 0});
			}
			assertUprightRows(document, 6, 2);
		}
	}

	@Test
	void shortStaircaseBlocksTakeNoFrame() throws IOException
	{
		// Three columns of two rows line up at some angle, so few rows tell nothing about skew
		try (PDDocument document = new PDDocument())
		{
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page))
			{
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
				float[][] blocks = {{0, 6, 12}, {12, 6, 0}};
				float y = 760;
				for (float[] offsets : blocks)
				{
					for (int row = 0; row < 2; row++)
					{
						for (int col = 0; col < offsets.length; col++)
						{
							drawWord(stream, 80 + col * 150, y - row * 24 - offsets[col], "Cell" + row + col);
						}
					}
					y -= 200;
				}
			}
			assertUprightRows(document, 5, 3);
		}
	}

	// Left column at the first offset under y, right column at the second one, six rows
	private static void drawOffsetBlock(PDPageContentStream stream, float y, float[] offsets) throws IOException
	{
		for (int row = 0; row < 6; row++)
		{
			for (int col = 0; col < offsets.length; col++)
			{
				drawWord(stream, 80 + col * 260, y - row * 22 - offsets[col], "Value" + row + col);
			}
		}
	}

	private static void drawWord(PDPageContentStream stream, float x, float y, String text) throws IOException
	{
		stream.beginText();
		stream.setTextMatrix(Matrix.getTranslateInstance(x, y));
		stream.showText(text);
		stream.endText();
	}

	// One table of the upright frame, a region frame would split the page into a table per block
	private static void assertUprightRows(PDDocument document, int rows, int columns) throws IOException
	{
		List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
		assertEquals(1, tables.size(), "one table expected");
		assertEquals(rows, tables.get(0).getRowCount(), "rows");
		assertEquals(columns, tables.get(0).getColumnCount(), "columns");
	}

	// Table of words on a grid skewed by theta from its first cell, every glyph at the angle glyphAngle
	private record Grid(double x, double y, double theta, double glyphAngle, int rows, int columns, float columnStep, float rowStep, String tag)
	{
		String cell(int row, int col)
		{
			return tag + row + "x" + col;
		}
	}

	private static PDDocument createGridsPdf(Grid... grids) throws IOException
	{
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.A4);
		document.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(document, page))
		{
			stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
			for (Grid grid : grids)
			{
				double angle = Math.toRadians(grid.theta());
				for (int row = 0; row < grid.rows(); row++)
				{
					for (int col = 0; col < grid.columns(); col++)
					{
						double localX = col * grid.columnStep();
						double localY = -row * grid.rowStep();
						stream.beginText();
						stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(grid.glyphAngle()),
							(float) (grid.x() + localX * Math.cos(angle) - localY * Math.sin(angle)),
							(float) (grid.y() + localX * Math.sin(angle) + localY * Math.cos(angle))));
						stream.showText(grid.cell(row, col));
						stream.endText();
					}
				}
			}
		}
		return document;
	}

	private static void assertGrid(Grid grid, TableModel table, String what)
	{
		assertEquals(grid.rows(), table.getRowCount(), what + " rows of " + grid.tag());
		assertEquals(grid.columns(), table.getColumnCount(), what + " columns of " + grid.tag());
		for (int row = 0; row < grid.rows(); row++)
		{
			for (int col = 0; col < grid.columns(); col++)
			{
				assertEquals(grid.cell(row, col), table.getCell(row, col).getText().trim(), what + " cell " + row + "," + col);
			}
		}
	}

	// Words at 0 degrees whose origins lie on a grid skewed by theta, like a block of an OCR text layer
	private static PDDocument createUprightWordsOnSkewedGrid(double theta, int rows, int columns, float columnStep, float rowStep,
		float fontSize, BiFunction<Integer, Integer, String> cell) throws IOException
	{
		double angle = Math.toRadians(theta);
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.A4);
		document.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(document, page))
		{
			stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
			for (int row = 0; row < rows; row++)
			{
				for (int col = 0; col < columns; col++)
				{
					float localX = col * columnStep;
					float localY = -row * rowStep;
					stream.beginText();
					stream.setTextMatrix(Matrix.getTranslateInstance(
						(float) (60 + localX * Math.cos(angle) - localY * Math.sin(angle)),
						(float) (780 + localX * Math.sin(angle) + localY * Math.cos(angle))));
					stream.showText(cell.apply(row, col));
					stream.endText();
				}
			}
		}
		return document;
	}

	@Test
	void turnedCellStaysInItsTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double theta : new double[] {0, 3})
		{
			for (double turn : new double[] {1.2, 2, 5, 30})
			{
				String what = "cell turned by " + turn + " in table at " + theta;
				try (PDDocument document = createCenteredRowsPdf(0, theta, (row, col) -> row == 2 && col == 1 ? turn : 0.0))
				{
					List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
					assertEquals(1, tables.size(), what + " one table expected");
					assertSameGrid(upright, tables.get(0), what);
				}
			}
		}
	}

	@Test
	void turnedLongWordStaysInItsTable() throws IOException
	{
		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try (PDDocument document = createRotatedRowsPdf(0, new float[] {COLUMN_X[0], Y_TOP, 0}))
		{
			try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0), PDPageContentStream.AppendMode.APPEND, false))
			{
				stream.beginText();
				stream.setFont(font, FONT_SIZE);
				stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(4), COLUMN_X[2], Y_TOP - 5 * ROW_STEP));
				stream.showText("Warehouse");
				stream.endText();
			}
			TableModel model = new PdfTableDetector().detectTables(document, 1).get(0);
			String text = model.getCell(5, 2).getText().trim();
			assertTrue(text.endsWith("house"), "turned word stays in its cell, was: " + text);
		}
	}

	@Test
	void longTurnedLineDoesNotBreakUprightTable() throws IOException
	{
		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		TableModel upright = detectUprightRows();
		for (double turn : new double[] {2, 5, 10, 20, 30, 45})
		{
			try (PDDocument document = createRotatedRowsPdf(0, new float[] {COLUMN_X[0], Y_TOP, 0}))
			{
				try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0), PDPageContentStream.AppendMode.APPEND, false))
				{
					stream.beginText();
					stream.setFont(font, FONT_SIZE);
					stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(turn), COLUMN_X[0], Y_TOP - 4 * ROW_STEP));
					stream.showText("CONFIDENTIAL DRAFT DOCUMENT DO NOT COPY");
					stream.endText();
				}
				String what = "line turned by " + turn;
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(1, tables.size(), what + " one table expected");
				assertSameGrid(upright, tables.get(0), what);
			}
		}
	}

	@Test
	void noisySkewedTableKeepsItsGrid() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int seed = 0; seed < NOISE_SEEDS; seed++)
		{
			String what = "table at 30 seed " + seed;
			Random random = new Random(seed);
			try (PDDocument document = createCenteredRowsPdf(0, 30, (row, col) -> uniform(random, 1.5)))
			{
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(1, tables.size(), what + " one table expected");
				assertSameGrid(upright, tables.get(0), what);
			}
		}
	}

	@Test
	void turnedTextOnUprightGridKeepsUprightGrid() throws IOException
	{
		TableModel upright = detectUprightRows();
		assertUprightTable(upright, (row, col) -> 5.0, "all cells at 5 degrees");
		assertUprightTable(upright, (row, col) -> col == 2 ? 5.0 : 0.0, "last column at 5 degrees");
		for (int seed = 0; seed < NOISE_SEEDS; seed++)
		{
			Random random = new Random(seed);
			assertUprightTable(upright, (row, col) -> uniform(random, 3), "random cell angles seed " + seed);
		}
	}

	@Test
	void noisyParagraphDoesNotTurnUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double theta : new double[] {1.2, 1.5})
		{
			for (int seed = 0; seed < NOISE_SEEDS; seed++)
			{
				String what = "paragraph at " + theta + " seed " + seed;
				Random random = new Random(seed);
				try (PDDocument document = createRotatedRowsPdf(0))
				{
					drawRotatedRows(document, document.getPage(0), COLUMN_X[0], Y_TOP, 0, (row, col) -> uniform(random, 0.4));
					drawParagraph(document, document.getPage(0), 40f, 400f, theta, () -> uniform(random, 0.4));
					List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
					assertEquals(1, tables.size(), what + " one table expected");
					assertSameGrid(upright, tables.get(0), what);
				}
			}
		}
	}

	@Test
	void chainOfParagraphAnglesDoesNotTurnUprightTable() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (double[] angles : new double[][] {{0.7, 1.5}, {0.9, 1.8}})
		{
			String what = "paragraphs at " + angles[0] + " and " + angles[1];
			try (PDDocument document = createRotatedRowsPdf(0, new float[] {COLUMN_X[0], Y_TOP, 0}))
			{
				drawParagraph(document, document.getPage(0), 40f, 540f, angles[0]);
				drawParagraph(document, document.getPage(0), 40f, 340f, angles[1]);
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				assertEquals(1, tables.size(), what + " one table expected");
				assertSameGrid(upright, tables.get(0), what);
			}
		}
	}

	@Test
	void uprightTextOnSkewedRowsFollowsParagraphFrame() throws IOException
	{
		// An OCR engine can write one block of a skewed page with text at exactly 0 degrees
		TableModel upright = detectUprightRows();
		double theta = 1.5;
		try (PDDocument document = createRotatedRowsPdf(0))
		{
			drawRotatedRows(document, document.getPage(0), COLUMN_X[0], Y_TOP - 20, theta, (row, col) -> -theta);
			drawParagraph(document, document.getPage(0), 40f, 400f, theta);
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			assertSameGrid(upright, tables.get(0), "text at 0 degrees on rows at 1.5 degrees");
		}
	}

	@Test
	void skewedCellBoxIsEnvelopeOfTurnedCell() throws IOException
	{
		try (PDDocument document = createCenteredRowsPdf(0, 30))
		{
			TableModel skewed = new PdfTableDetector().detectTables(document, 1).get(0);
			BoundingBox upright = detectUprightRows().getCell(1, 0).getBoundingBox();
			BoundingBox envelope = skewed.getCell(1, 0).getBoundingBox();

			double cos = Math.cos(Math.toRadians(30));
			double sin = Math.sin(Math.toRadians(30));
			assertEquals(upright.getWidth() * cos + upright.getHeight() * sin, envelope.getWidth(), 0.01f, "envelope width");
			assertEquals(upright.getWidth() * sin + upright.getHeight() * cos, envelope.getHeight(), 0.01f, "envelope height");
		}
	}

	@Test
	void uprightAndSkewedTablesAreDetectedSeparately() throws IOException
	{
		TableModel upright = detectUprightRows();
		try (PDDocument document = createRotatedRowsPdf(0,
			new float[] {COLUMN_X[0], Y_TOP, 0}, new float[] {COLUMN_X[0], 300f, 2}))
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(2, tables.size(), "two tables expected");
			assertSameGrid(upright, tables.get(0), "upright table");
			assertSameGrid(upright, tables.get(1), "skewed table");
			assertBoxesCoverDisplayedText(document, tables, "upright and skewed");
		}
	}

	@Test
	void tablesInTwoDirectionsAreDetectedSeparately() throws IOException
	{
		TableModel upright = detectUprightRows();
		for (int rotation : new int[] {0, 90})
		{
			// One horizontal table at the top and one turned by 90 degrees in the lower right part
			try (PDDocument document = createRotatedRowsPdf(rotation,
				new float[] {COLUMN_X[0], Y_TOP, 0}, new float[] {450f, 100f, 90}))
			{
				List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
				String what = "rotation " + rotation;
				assertEquals(2, tables.size(), what + " two tables expected");
				assertSameGrid(upright, tables.get(0), what + " horizontal table");
				assertSameGrid(upright, tables.get(1), what + " vertical table");
				assertBoxesCoverDisplayedText(document, tables, what);

				// Direction 0 is searched first, so table-1 is the horizontal table
				assertEquals("table-1", tables.get(0).getId());
				assertEquals("table-2", tables.get(1).getId());
			}
		}
	}

	@Test
	void verticalLabelDoesNotSplitHorizontalTable() throws IOException
	{
		try (PDDocument document = createSyntheticPdf(PDRectangle.A4, 0, 0f, Y_TOP))
		{
			// Label rotated by 90 degrees in the left margin, its glyphs run upward across the table rows
			PDPage page = document.getPage(0);
			try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false))
			{
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
				stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(90), 60f, Y_TOP - 7 * ROW_STEP));
				stream.showText("Vertical side label");
				stream.endText();
			}
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			assertSameGrid(table, tables.get(0), "with vertical label");
		}
	}

	@Test
	void offsetMediaBoxLimitsTableToText() throws IOException
	{
		PDRectangle shifted = new PDRectangle(100, 100, 595, 842);
		TableModel model = detectSingle(shifted, 0, 100f, 850f);
		assertFiniteWithin(model, shifted.getUpperRightX());
		assertEquals(COLUMN_X[0], model.getBoundingBox().getX(), 0.5f);
	}

	@Test
	void pageAtMaxCoordinateKeepsTableBoundingBox() throws IOException
	{
		// Text near the right edge of the largest PDF page must survive the TableModel guard
		float width = 14400f;
		float xOffset = width - COLUMN_X[2] - 100f;
		TableModel model = detectSingle(new PDRectangle(width, 842), 0, xOffset, 750f);
		assertFiniteWithin(model, width);
		assertEquals(xOffset + COLUMN_X[0], model.getBoundingBox().getX(), 0.5f);
	}

	@Test
	void boundaryInsideWordIsRemoved() throws IOException
	{
		// The long word reaches past the Name | Qty gap midpoint of the other rows (issue #200)
		TableModel model = detectRows(new String[][] {
			{"Name", "Qty", "Price"},
			{"Apple", "3", "1.20"},
			{"Blackcurrantjamjarlabel", "5", "2.20"},
			{"Pear", "7", "3.20"},
			{"Plum", "9", "4.20"},
		});
		assertEquals(5, model.getRowCount());
		assertEquals(2, model.getColumnCount());
		assertEquals("Blackcurrantjamjarlabel5", model.getCell(2, 0).getText());
		assertEquals("2.20", model.getCell(2, 1).getText());
	}

	// Running text page layout in PDF points
	private static final float PROSE_X = 90f;

	private static final float PROSE_WIDTH = 415f;

	// Lines are wrapped narrower than PROSE_WIDTH, justification then widens the word gaps
	private static final float PROSE_WRAP_WIDTH = 330f;

	private static final float LINE_STEP = 14f;

	private static final float PARAGRAPH_STEP = 10f;

	private static final float MATRIX_STEP = 16f;

	private static final float[] MATRIX_X = {100f, 250f, 350f, 450f};

	private static final String PROSE = "The design report describes the load bearing structure of the hall and the "
		+ "assumptions used for the static analysis. Each section lists the inputs, the method and the results, "
		+ "so that the reviewer can follow the calculation without the original model. Deviations from the "
		+ "standard are marked in the text and explained in the closing chapter of this report.";

	// Requirement matrix, the first row is the header
	private static final String[][] MATRIX = {
		{"Requirement", "Unit", "Limit", "Result"},
		{"Roof load", "kN/m2", "1.50", "1.32"},
		{"Snow load", "kN/m2", "1.00", "0.85"},
		{"Wind pressure", "kPa", "0.80", "0.64"},
		{"Beam deflection", "mm", "40", "31"},
		{"Column buckling", "-", "1.00", "0.72"},
		{"Fire resistance", "min", "30", "45"},
		{"Anchor tension", "kN", "25", "19"},
		{"Weld stress", "MPa", "235", "180"},
		{"Floor vibration", "Hz", "3.0", "4.2"},
	};

	// Short line before the matrix, it crosses the matrix columns but is no running text
	private static final String MATRIX_INTRO = "All checked values are listed below:";

	// One line of a synthetic page, a single string is a line of text at PROSE_X, more strings are a matrix row
	// A justified line spreads its words to PROSE_WIDTH
	private record Line(float y, boolean justified, String... texts)
	{
		Line(float y, String... texts)
		{
			this(y, false, texts);
		}
	}

	private static List<String> wrap(PDType1Font font, String text) throws IOException
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && width(font, candidate) > PROSE_WRAP_WIDTH)
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		lines.add(line.toString());
		return lines;
	}

	private static float width(PDType1Font font, String text) throws IOException
	{
		return font.getStringWidth(text) / 1000f * FONT_SIZE;
	}

	// Heading followed by a paragraph of running text, returns the y below the paragraph
	private static float addSection(List<Line> lines, PDType1Font font, String heading, float y) throws IOException
	{
		lines.add(new Line(y, heading));
		y -= LINE_STEP + PARAGRAPH_STEP;
		List<String> paragraph = wrap(font, PROSE);
		for (int i = 0; i < paragraph.size(); i++)
		{
			lines.add(new Line(y, i < paragraph.size() - 1, paragraph.get(i)));
			y -= LINE_STEP;
		}
		return y - PARAGRAPH_STEP;
	}

	private static PDDocument createPage(List<Line> lines)	throws IOException
	{
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.A4);
		document.addPage(page);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try (PDPageContentStream stream = new PDPageContentStream(document, page))
		{
			for (Line line : lines)
			{
				if (line.texts().length == 1)
				{
					drawWords(stream, font, line);
					continue;
				}
				for (int col = 0; col < line.texts().length; col++)
				{
					stream.beginText();
					stream.setFont(font, FONT_SIZE);
					stream.newLineAtOffset(MATRIX_X[col], line.y());
					stream.showText(line.texts()[col]);
					stream.endText();
				}
			}
		}
		return document;
	}

	// Words are drawn one by one without space glyphs, like justified text with a CID font
	private static void drawWords(PDPageContentStream stream, PDType1Font font, Line line) throws IOException
	{
		String[] words = line.texts()[0].split(" ");
		float space = width(font, " ");
		if (line.justified() && words.length > 1)
		{
			float wordsWidth = 0;
			for (String word : words)
			{
				wordsWidth += width(font, word);
			}
			space = (PROSE_WIDTH - wordsWidth) / (words.length - 1);
		}
		float x = PROSE_X;
		for (String word : words)
		{
			stream.beginText();
			stream.setFont(font, FONT_SIZE);
			stream.newLineAtOffset(x, line.y());
			stream.showText(word);
			stream.endText();
			x += width(font, word) + space;
		}
	}

	// Page with headings and paragraphs of justified running text only
	static PDDocument createProsePdf() throws IOException
	{
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		List<Line> lines = new ArrayList<>();
		float y = 780f;
		for (String heading : new String[] {"1 Introduction", "2 Structure", "3 Loads", "4 Results"})
		{
			y = addSection(lines, font, heading, y);
		}
		return createPage(lines);
	}

	// Page with justified paragraphs, the requirement matrix right after a short line and more paragraphs
	static PDDocument createProseWithMatrixPdf() throws IOException
	{
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		List<Line> lines = new ArrayList<>();
		float y = addSection(lines, font, "1 Introduction", 780f);
		lines.add(new Line(y, MATRIX_INTRO));
		y -= LINE_STEP;
		for (String[] row : MATRIX)
		{
			lines.add(new Line(y, row));
			y -= MATRIX_STEP;
		}
		y = addSection(lines, font, "2 Results", y - PARAGRAPH_STEP);
		addSection(lines, font, "3 Conclusion", y);
		return createPage(lines);
	}

	@Test
	void runningTextPageHasNoTable() throws IOException
	{
		try (PDDocument document = createProsePdf())
		{
			assertEquals(0, new PdfTableDetector().detectTables(document, 1).size());
		}
	}

	@Test
	void matrixBetweenParagraphsIsItsOwnTable() throws IOException
	{
		TableModel model;
		try (PDDocument document = createProseWithMatrixPdf())
		{
			List<TableModel> tables = new PdfTableDetector().detectTables(document, 1);
			assertEquals(1, tables.size(), "one table expected");
			model = tables.get(0);
		}
		assertEquals(MATRIX.length, model.getRowCount());
		assertEquals(MATRIX[0].length, model.getColumnCount());
		for (int row = 0; row < MATRIX.length; row++)
		{
			for (int col = 0; col < MATRIX[row].length; col++)
			{
				assertEquals(MATRIX[row][col], model.getCell(row, col).getText(), "cell " + row + "," + col);
			}
		}

		// Bounding box covers the matrix only, not the intro line or the paragraphs around it
		BoundingBox bbox = model.getBoundingBox();
		float firstBaseline = model.getCell(0, 0).getBoundingBox().getBottom();
		float lastBaseline = model.getCell(MATRIX.length - 1, 0).getBoundingBox().getBottom();
		assertEquals(MATRIX_X[0], bbox.getX(), 0.5f);
		assertTrue(bbox.getY() > firstBaseline - MATRIX_STEP, "table starts above the matrix: " + bbox.getY());
		assertEquals(lastBaseline, bbox.getBottom(), DELTA);
		assertTrue(right(bbox) < MATRIX_X[3] + 50f, "table wider than the matrix: " + right(bbox));
	}
}
