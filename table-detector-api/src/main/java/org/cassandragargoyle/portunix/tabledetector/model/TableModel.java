/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a detected table in a document
 *
 * @author Zdenek
 * @since 2026-02-03
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableModel
{
	// Largest PDF user space coordinate (200 inches * 72 points), anything beyond is not a real coordinate
	static final float MAX_COORDINATE = 14400f;

	@JsonProperty("id")
	private String id;

	@JsonProperty("page")
	private int page;

	@JsonProperty("boundingBox")
	private BoundingBox boundingBox;

	@JsonProperty("confidence")
	private float confidence;

	@JsonProperty("rows")
	private int rowCount;

	@JsonProperty("columns")
	private int columnCount;

	@JsonProperty("cells")
	private List<TableCell> cells;

	@JsonProperty("hasBorders")
	private boolean hasBorders;

	public TableModel()
	{
		this.cells = new ArrayList<>();
	}

	public TableModel(String id, int page)
	{
		this.id = id;
		this.page = page;
		this.cells = new ArrayList<>();
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}

	public int getPage()
	{
		return page;
	}

	public void setPage(int page)
	{
		this.page = page;
	}

	public BoundingBox getBoundingBox()
	{
		return boundingBox;
	}

	public void setBoundingBox(BoundingBox boundingBox)
	{
		this.boundingBox = boundingBox;
	}

	public float getConfidence()
	{
		return confidence;
	}

	public void setConfidence(float confidence)
	{
		this.confidence = confidence;
	}

	public int getRowCount()
	{
		return rowCount;
	}

	public void setRowCount(int rowCount)
	{
		this.rowCount = rowCount;
	}

	public int getColumnCount()
	{
		return columnCount;
	}

	public void setColumnCount(int columnCount)
	{
		this.columnCount = columnCount;
	}

	public List<TableCell> getCells()
	{
		return cells;
	}

	public void setCells(List<TableCell> cells)
	{
		this.cells = cells;
	}

	public void addCell(TableCell cell)
	{
		this.cells.add(cell);
	}

	public boolean isHasBorders()
	{
		return hasBorders;
	}

	public void setHasBorders(boolean hasBorders)
	{
		this.hasBorders = hasBorders;
	}

	/**
	 * Get cell at specific position
	 */
	public TableCell getCell(int row, int column)
	{
		for (TableCell cell : cells)
		{
			if (cell.getRow() == row && cell.getColumn() == column)
			{
				return cell;
			}
		}
		return null;
	}

	/**
	 * Calculate bounding box from all cells
	 */
	public void calculateBoundingBox()
	{
		if (cells.isEmpty())
		{
			return;
		}

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		for (TableCell cell : cells)
		{
			BoundingBox bbox = cell.getBoundingBox();
			if (isSane(bbox))
			{
				minX = Math.min(minX, bbox.getX());
				minY = Math.min(minY, bbox.getY());
				maxX = Math.max(maxX, bbox.getX() + bbox.getWidth());
				maxY = Math.max(maxY, bbox.getY() + bbox.getHeight());
			}
		}

		if (minX != Float.MAX_VALUE)
		{
			this.boundingBox = new BoundingBox(minX, minY, maxX - minX, maxY - minY);
		}
	}

	/**
	 * Check that a cell bounding box is finite and fits into the largest possible PDF page
	 * so one bad cell cannot spread the table bounding box
	 */
	private static boolean isSane(BoundingBox bbox)
	{
		if (bbox == null)
		{
			return false;
		}
		float[] values = {bbox.getX(), bbox.getY(), bbox.getWidth(), bbox.getHeight()};
		for (float value : values)
		{
			if (!Float.isFinite(value) || Math.abs(value) > MAX_COORDINATE)
			{
				return false;
			}
		}
		return Math.abs(bbox.getX() + bbox.getWidth()) <= MAX_COORDINATE
			&& Math.abs(bbox.getY() + bbox.getHeight()) <= MAX_COORDINATE;
	}

	@Override
	public String toString()
	{
		return String.format("Table[%s, page=%d, %dx%d, cells=%d]",
			id, page, rowCount, columnCount, cells.size());
	}
}
