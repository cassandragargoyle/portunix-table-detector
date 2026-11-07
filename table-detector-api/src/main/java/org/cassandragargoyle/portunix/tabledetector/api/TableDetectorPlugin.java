/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.api;

import org.pf4j.ExtensionPoint;

/**
 * Extension point interface for format-specific table detection backends
 *
 * @author Zdenek
 * @since 2026-02-03
 */
public interface TableDetectorPlugin extends ExtensionPoint
{
	/**
	 * Unique identifier for this detector (e.g. "pdf", "docx", "xlsx", "html")
	 */
	String getId();

	/**
	 * MIME types this detector can handle
	 */
	String[] getSupportedMimeTypes();

	/**
	 * Check if this detector can handle the given context
	 */
	boolean supports(DetectionContext context);

	/**
	 * Detect tables in the given document
	 */
	DetectionResult detect(DetectionContext context);

	/**
	 * Priority for format overlap resolution (lower = higher priority)
	 */
	int getPriority();
}
