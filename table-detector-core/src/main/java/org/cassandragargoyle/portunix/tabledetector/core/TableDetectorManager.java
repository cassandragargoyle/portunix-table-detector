/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.core;

import org.cassandragargoyle.portunix.tabledetector.api.DetectionContext;
import org.cassandragargoyle.portunix.tabledetector.api.DetectionResult;
import org.cassandragargoyle.portunix.tabledetector.api.TableDetectorPlugin;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cassandragargoyle.api.log.LogFactory;

/**
 * Manages table detection plugins and routes detection requests by MIME type
 * Supports both PF4J plugin discovery and manual plugin registration
 *
 * @author Zdenek
 * @since 2026-02-03
 */
public class TableDetectorManager
{
	private static final Logger LOG = LogFactory.getLogger(TableDetectorManager.class);

	private final List<TableDetectorPlugin> plugins = new ArrayList<>();

	private PluginManager pluginManager;

	private boolean initialized = false;

	/**
	 * Initialize with PF4J plugin discovery
	 * Loads plugins from the default plugin directory
	 */
	public void initialize()
	{
		if (initialized)
		{
			return;
		}

		try
		{
			pluginManager = new DefaultPluginManager();
			pluginManager.loadPlugins();
			pluginManager.startPlugins();

			List<TableDetectorPlugin> discovered = pluginManager.getExtensions(TableDetectorPlugin.class);
			plugins.addAll(discovered);

			LOG.log(Level.FINE, "Initialized table detector with {0} PF4J plugin(s)", discovered.size());
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "PF4J plugin discovery failed, using manually registered plugins only: {0}", e.getMessage());
		}

		initialized = true;
	}

	/**
	 * Register a plugin manually (without PF4J discovery)
	 * Useful for embedded usage (e.g. in text-extractor)
	 */
	public void registerPlugin(TableDetectorPlugin plugin)
	{
		plugins.add(plugin);
		LOG.log(Level.FINER, "Registered table detector plugin: {0}", plugin.getId());
	}

	/**
	 * Detect tables in the given context
	 * Routes to the appropriate plugin based on MIME type
	 */
	public DetectionResult detect(DetectionContext context)
	{
		long startTime = System.currentTimeMillis();

		TableDetectorPlugin plugin = findPlugin(context);
		if (plugin == null)
		{
			return DetectionResult.failure("No table detector plugin found for MIME type: " + context.getMimeType());
		}

		DetectionResult result = plugin.detect(context);
		result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

		// Filter by confidence threshold
		if (result.isSuccess() && context.getMinConfidence() > 0)
		{
			List<TableModel> filtered = result.getTables().stream()
				.filter(t -> t.getConfidence() >= context.getMinConfidence())
				.toList();

			if (filtered.size() != result.getTables().size())
			{
				result = DetectionResult.success(filtered);
				result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
			}
		}

		return result;
	}

	/**
	 * Find the best plugin for the given context
	 */
	private TableDetectorPlugin findPlugin(DetectionContext context)
	{
		return plugins.stream()
			.filter(p -> p.supports(context))
			.min(Comparator.comparingInt(TableDetectorPlugin::getPriority))
			.orElse(null);
	}

	/**
	 * Get all registered plugins
	 */
	public List<TableDetectorPlugin> getPlugins()
	{
		return List.copyOf(plugins);
	}

	/**
	 * Shutdown and cleanup
	 */
	public void shutdown()
	{
		if (pluginManager != null)
		{
			pluginManager.stopPlugins();
			pluginManager.unloadPlugins();
		}
		plugins.clear();
		initialized = false;
	}
}
