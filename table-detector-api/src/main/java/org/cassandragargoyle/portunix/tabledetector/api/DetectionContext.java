/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.api;

/**
 * Input context for table detection
 *
 * @author Zdenek
 * @since 2026-02-03
 */
public class DetectionContext
{
	private byte[] content;
	private String mimeType;
	private String fileName;
	private int page = -1; // -1 = all pages
	private float minConfidence = 0.5f;
	private boolean debug = false;

	public DetectionContext()
	{
	}

	public byte[] getContent()
	{
		return content;
	}

	public DetectionContext setContent(byte[] content)
	{
		this.content = content;
		return this;
	}

	public String getMimeType()
	{
		return mimeType;
	}

	public DetectionContext setMimeType(String mimeType)
	{
		this.mimeType = mimeType;
		return this;
	}

	public String getFileName()
	{
		return fileName;
	}

	public DetectionContext setFileName(String fileName)
	{
		this.fileName = fileName;
		return this;
	}

	public int getPage()
	{
		return page;
	}

	public DetectionContext setPage(int page)
	{
		this.page = page;
		return this;
	}

	public boolean isAllPages()
	{
		return page < 0;
	}

	public float getMinConfidence()
	{
		return minConfidence;
	}

	public DetectionContext setMinConfidence(float minConfidence)
	{
		this.minConfidence = minConfidence;
		return this;
	}

	public boolean isDebug()
	{
		return debug;
	}

	public DetectionContext setDebug(boolean debug)
	{
		this.debug = debug;
		return this;
	}
}
