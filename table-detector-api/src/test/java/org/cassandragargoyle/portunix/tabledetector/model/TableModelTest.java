/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for TableModel bounding box calculation
 *
 * @author Zdenek
 * @since 2026-10-25
 */
class TableModelTest
{
	private static final float DELTA = 0.001f;

	private static TableCell cell(int row, int column, BoundingBox bbox)
	{
		TableCell cell = new TableCell(row, column, "x");
		cell.setBoundingBox(bbox);
		return cell;
	}

	@Test
	void unionOfValidCells()
	{
		TableModel table = new TableModel("t", 1);
		table.addCell(cell(0, 0, new BoundingBox(100, 50, 40, 10)));
		table.addCell(cell(0, 1, new BoundingBox(200, 50, 30, 10)));
		table.addCell(cell(1, 0, new BoundingBox(100, 70, 20, 12)));

		table.calculateBoundingBox();

		BoundingBox bbox = table.getBoundingBox();
		assertEquals(100, bbox.getX(), DELTA);
		assertEquals(50, bbox.getY(), DELTA);
		assertEquals(130, bbox.getWidth(), DELTA);
		assertEquals(32, bbox.getHeight(), DELTA);
	}

	@Test
	void skipsNonFiniteCell()
	{
		TableModel table = new TableModel("t", 1);
		table.addCell(cell(0, 0, new BoundingBox(100, 50, 40, 10)));
		table.addCell(cell(0, 1, new BoundingBox(480, 50, Float.MAX_VALUE, 10)));
		table.addCell(cell(0, 2, new BoundingBox(Float.NaN, 50, 10, 10)));
		table.addCell(cell(0, 3, new BoundingBox(300, 50, Float.POSITIVE_INFINITY, 10)));

		table.calculateBoundingBox();

		BoundingBox bbox = table.getBoundingBox();
		assertEquals(100, bbox.getX(), DELTA);
		assertEquals(40, bbox.getWidth(), DELTA);
	}

	@Test
	void skipsOversizedCell()
	{
		TableModel table = new TableModel("t", 1);
		table.addCell(cell(0, 0, new BoundingBox(100, 50, 40, 10)));
		table.addCell(cell(0, 1, new BoundingBox(0, 50, TableModel.MAX_COORDINATE + 1, 10)));
		table.addCell(cell(0, 2, new BoundingBox(TableModel.MAX_COORDINATE - 1, 50, 10, 10)));

		table.calculateBoundingBox();

		BoundingBox bbox = table.getBoundingBox();
		assertEquals(100, bbox.getX(), DELTA);
		assertEquals(40, bbox.getWidth(), DELTA);
	}

	@Test
	void noBoundingBoxWhenAllCellsInvalid()
	{
		TableModel table = new TableModel("t", 1);
		table.addCell(cell(0, 0, new BoundingBox(0, 50, Float.MAX_VALUE, 10)));
		table.addCell(cell(0, 1, null));

		table.calculateBoundingBox();

		assertNull(table.getBoundingBox());
	}

	@Test
	void negativeCoordinatesProduceCorrectMaximum()
	{
		TableModel table = new TableModel("t", 1);
		table.addCell(cell(0, 0, new BoundingBox(-50, -40, 20, 10)));

		table.calculateBoundingBox();

		BoundingBox bbox = table.getBoundingBox();
		assertEquals(-50, bbox.getX(), DELTA);
		assertEquals(20, bbox.getWidth(), DELTA);
		assertEquals(10, bbox.getHeight(), DELTA);
	}
}
