/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.api;

import org.cassandragargoyle.portunix.tabledetector.model.TableModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of table detection
 *
 * @author Zdenek
 * @since 2026-02-03
 */
public class DetectionResult
{
	private boolean success;
	private List<TableModel> tables;
	private long processingTimeMs;
	private Map<String, String> metadata;
	private String errorMessage;

	private DetectionResult()
	{
		this.metadata = new HashMap<>();
	}

	public static DetectionResult success(List<TableModel> tables)
	{
		DetectionResult result = new DetectionResult();
		result.success = true;
		result.tables = tables;
		return result;
	}

	public static DetectionResult failure(String errorMessage)
	{
		DetectionResult result = new DetectionResult();
		result.success = false;
		result.tables = Collections.emptyList();
		result.errorMessage = errorMessage;
		return result;
	}

	public boolean isSuccess()
	{
		return success;
	}

	public List<TableModel> getTables()
	{
		return tables;
	}

	public long getProcessingTimeMs()
	{
		return processingTimeMs;
	}

	public void setProcessingTimeMs(long processingTimeMs)
	{
		this.processingTimeMs = processingTimeMs;
	}

	public Map<String, String> getMetadata()
	{
		return metadata;
	}

	public void addMetadata(String key, String value)
	{
		this.metadata.put(key, value);
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}
}
