/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single cell in a detected table
 *
 * @author Zdenek
 * @since 2026-02-03
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableCell
{
	@JsonProperty("row")
	private int row;

	@JsonProperty("column")
	private int column;

	@JsonProperty("text")
	private String text;

	@JsonProperty("isHeader")
	private boolean header;

	@JsonProperty("rowSpan")
	private int rowSpan = 1;

	@JsonProperty("colSpan")
	private int colSpan = 1;

	@JsonProperty("boundingBox")
	private BoundingBox boundingBox;

	public TableCell()
	{
	}

	public TableCell(int row, int column, String text)
	{
		this.row = row;
		this.column = column;
		this.text = text;
	}

	public int getRow()
	{
		return row;
	}

	public void setRow(int row)
	{
		this.row = row;
	}

	public int getColumn()
	{
		return column;
	}

	public void setColumn(int column)
	{
		this.column = column;
	}

	public String getText()
	{
		return text;
	}

	public void setText(String text)
	{
		this.text = text;
	}

	public boolean isHeader()
	{
		return header;
	}

	public void setHeader(boolean header)
	{
		this.header = header;
	}

	public int getRowSpan()
	{
		return rowSpan;
	}

	public void setRowSpan(int rowSpan)
	{
		this.rowSpan = rowSpan;
	}

	public int getColSpan()
	{
		return colSpan;
	}

	public void setColSpan(int colSpan)
	{
		this.colSpan = colSpan;
	}

	public BoundingBox getBoundingBox()
	{
		return boundingBox;
	}

	public void setBoundingBox(BoundingBox boundingBox)
	{
		this.boundingBox = boundingBox;
	}

	@Override
	public String toString()
	{
		return String.format("TableCell[%d,%d]='%s'", row, column, text);
	}
}
