/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a bounding box with coordinates in a document coordinate system
 * Origin is the top-left corner of the page, x grows to the right and y grows downward, units are PDF points
 * y is the top edge of the box and getBottom() the bottom edge, not the text baseline
 * A box of skewed text is the axis-aligned envelope of the turned rectangle, neighbouring boxes may overlap
 *
 * @author Zdenek
 * @since 2026-02-03
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoundingBox
{
	@JsonProperty("x")
	private float x;

	@JsonProperty("y")
	private float y;

	@JsonProperty("width")
	private float width;

	@JsonProperty("height")
	private float height;

	public BoundingBox()
	{
	}

	public BoundingBox(float x, float y, float width, float height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public float getX()
	{
		return x;
	}

	public void setX(float x)
	{
		this.x = x;
	}

	public float getY()
	{
		return y;
	}

	public void setY(float y)
	{
		this.y = y;
	}

	public float getWidth()
	{
		return width;
	}

	public void setWidth(float width)
	{
		this.width = width;
	}

	public float getHeight()
	{
		return height;
	}

	public void setHeight(float height)
	{
		this.height = height;
	}

	public float getRight()
	{
		return x + width;
	}

	public float getBottom()
	{
		return y + height;
	}

	/**
	 * Check if this bounding box horizontally overlaps with another
	 */
	public boolean horizontallyOverlaps(BoundingBox other)
	{
		return !(this.getRight() < other.getX() || other.getRight() < this.getX());
	}

	/**
	 * Check if this bounding box is in the same column as another (with tolerance)
	 */
	public boolean isInSameColumn(BoundingBox other, float tolerance)
	{
		float thisCenter = this.x + this.width / 2;
		float otherCenter = other.x + other.width / 2;
		return Math.abs(thisCenter - otherCenter) < tolerance;
	}

	/**
	 * Expand this bounding box to include another
	 */
	public void expandToInclude(BoundingBox other)
	{
		float newX = Math.min(this.x, other.x);
		float newY = Math.min(this.y, other.y);
		float newRight = Math.max(this.getRight(), other.getRight());
		float newBottom = Math.max(this.getBottom(), other.getBottom());

		this.x = newX;
		this.y = newY;
		this.width = newRight - newX;
		this.height = newBottom - newY;
	}

	@Override
	public String toString()
	{
		return String.format("BoundingBox[x=%.1f, y=%.1f, w=%.1f, h=%.1f]", x, y, width, height);
	}
}
