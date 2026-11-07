/*
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.cassandragargoyle.portunix.tabledetector.api.DetectionContext;
import org.cassandragargoyle.portunix.tabledetector.api.DetectionResult;
import org.cassandragargoyle.portunix.tabledetector.model.TableCell;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;
import org.cassandragargoyle.portunix.tabledetector.pdf.PdfTableDetectorPlugin;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI entry point for the Table Detector plugin
 *
 * @author Zdenek
 * @since 2026-02-03
 */
@Command(
	name = "table-detector",
	description = "Universal table detection framework with pluggable format backends",
	mixinStandardHelpOptions = true,
	version = "table-detector 1.0.0",
	subcommands = {Main.DetectCommand.class}
)
public class Main implements Runnable
{
	/**
	 * Get command prefix for help/usage output
	 * Uses PORTUNIX_PLUGIN_PREFIX env variable when invoked via portunix
	 */
	static String getCommandPrefix()
	{
		String prefix = System.getenv("PORTUNIX_PLUGIN_PREFIX");
		return (prefix != null && !prefix.isEmpty()) ? prefix : "table-detector";
	}

	@Override
	public void run()
	{
		// No subcommand provided - show help
		new CommandLine(this).usage(System.out);
	}

	public static void main(String[] args)
	{
		// Force UTF-8 output encoding for Windows compatibility
		System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

		int exitCode = new CommandLine(new Main())
			.setCommandName(getCommandPrefix())
			.execute(args);
		System.exit(exitCode);
	}

	@Command(
		name = "detect",
		description = "Detect tables in a document",
		mixinStandardHelpOptions = true
	)
	static class DetectCommand implements Callable<Integer>
	{
		@Parameters(index = "0", description = "Input file path")
		private File inputFile;

		@Option(names = {"-p", "--page"}, description = "Page number (0 = all pages, default: 0)", defaultValue = "0")
		private int page;

		@Option(names = {"-c", "--min-confidence"}, description = "Minimum confidence threshold (0.0-1.0, default: 0.5)", defaultValue = "0.5")
		private float minConfidence;

		@Option(names = {"-f", "--format"}, description = "Output format: json, markdown (default: json)", defaultValue = "json")
		private String format;

		@Option(names = {"-v", "--verbose"}, description = "Verbose output")
		private boolean verbose;

		@Override
		public Integer call() throws Exception
		{
			if (!inputFile.exists())
			{
				System.err.println("Error: File not found: " + inputFile.getPath());
				return 1;
			}

			if (verbose)
			{
				System.err.println("Processing: " + inputFile.getPath());
				System.err.println("Page: " + (page == 0 ? "all" : page));
				System.err.println("Min confidence: " + minConfidence);
				System.err.println("Format: " + format);
			}

			// Read file content
			byte[] content = Files.readAllBytes(inputFile.toPath());

			// Build detection context
			DetectionContext context = new DetectionContext()
				.setContent(content)
				.setFileName(inputFile.getName())
				.setMimeType(detectMimeType(inputFile.getName()))
				.setMinConfidence(minConfidence)
				.setDebug(verbose);

			if (page > 0)
			{
				context.setPage(page);
			}

			// Initialize manager with PDF backend
			TableDetectorManager manager = new TableDetectorManager();
			manager.registerPlugin(new PdfTableDetectorPlugin());

			try
			{
				DetectionResult result = manager.detect(context);

				if (!result.isSuccess())
				{
					System.err.println("Error: " + result.getErrorMessage());
					return 1;
				}

				if (verbose)
				{
					System.err.println("Tables found: " + result.getTables().size());
					System.err.println("Processing time: " + result.getProcessingTimeMs() + "ms");
				}

				// Output results
				if ("markdown".equalsIgnoreCase(format))
				{
					outputMarkdown(result.getTables());
				}
				else
				{
					outputJson(result);
				}

				return 0;
			}
			finally
			{
				manager.shutdown();
			}
		}

		private void outputJson(DetectionResult result) throws Exception
		{
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);

			Map<String, Object> output = new LinkedHashMap<>();
			output.put("success", result.isSuccess());
			output.put("tableCount", result.getTables().size());
			output.put("processingTimeMs", result.getProcessingTimeMs());
			output.put("tables", result.getTables());

			System.out.println(mapper.writeValueAsString(output));
		}

		private void outputMarkdown(List<TableModel> tables)
		{
			for (int t = 0; t < tables.size(); t++)
			{
				TableModel table = tables.get(t);

				if (t > 0)
				{
					System.out.println();
				}

				if (verbose)
				{
					System.out.println("<!-- " + table.getId()
						+ " (page " + table.getPage()
						+ ", confidence " + String.format("%.2f", table.getConfidence())
						+ ", " + table.getRowCount() + "x" + table.getColumnCount()
						+ ") -->");
				}

				for (int row = 0; row < table.getRowCount(); row++)
				{
					StringBuilder line = new StringBuilder("|");
					for (int col = 0; col < table.getColumnCount(); col++)
					{
						TableCell cell = table.getCell(row, col);
						String text = (cell != null && cell.getText() != null)
							? cell.getText().replace("|", "\\|").trim()
							: "";
						line.append(" ").append(text).append(" |");
					}
					System.out.println(line);

					// Separator after first row (header)
					if (row == 0)
					{
						StringBuilder sep = new StringBuilder("|");
						for (int col = 0; col < table.getColumnCount(); col++)
						{
							sep.append(" --- |");
						}
						System.out.println(sep);
					}
				}
			}
		}

		private String detectMimeType(String fileName)
		{
			if (fileName == null)
			{
				return null;
			}
			String lower = fileName.toLowerCase();
			if (lower.endsWith(".pdf"))
			{
				return "application/pdf";
			}
			return null;
		}
	}
}
