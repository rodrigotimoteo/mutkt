package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import java.io.File

/**
 * Generates interactive HTML graph of test-mutant relationships.
 */
object MutationGraphGenerator {
    /**
     * Generate interactive HTML graph.
     */
    fun generate(
        report: MutationReport,
        outputDir: File,
    ): File {
        val nodes = buildNodes(report)
        val edges = buildEdges(report)

        val html =
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Mutation Test Graph</title>
                <script src="https://d3js.org/d3.v7.min.js"></script>
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 { color: #333; }
                    .graph { background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .node { cursor: pointer; }
                    .node.target { fill: #4CAF50; }
                    .node.mutation.killed { fill: #f44336; }
                    .node.mutation.survived { fill: #FF9800; }
                    .node.mutation.weak { fill: #FFEB3B; }
                    .node.mutation.error { fill: #9E9E9E; }
                    .link { stroke: #999; stroke-opacity: 0.6; }
                    .link.killed { stroke: #f44336; stroke-width: 2px; }
                    .link.survived { stroke: #FF9800; stroke-width: 1px; stroke-dasharray: 5,5; }
                    .link.weak { stroke: #8BC34A; stroke-width: 1px; }
                    .link.error { stroke: #f44336; stroke-width: 1px; stroke-dasharray: 2,2; }
                    .link.timeout { stroke: #FF9800; stroke-width: 1px; stroke-dasharray: 1,3; }
                    .link.no_coverage { stroke: #9E9E9E; stroke-width: 1px; }
                    .tooltip { position: absolute; background: white; border: 1px solid #ccc; padding: 10px; border-radius: 4px; pointer-events: none; }
                    .legend { margin: 20px 0; }
                    .legend-item { display: inline-block; margin-right: 20px; }
                    .legend-color { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 5px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Mutation Test Graph</h1>
                    <div class="legend">
                        <div class="legend-item"><span class="legend-color" style="background: #4CAF50;"></span>Target Class</div>
                        <div class="legend-item"><span class="legend-color" style="background: #f44336;"></span>Killed</div>
                        <div class="legend-item"><span class="legend-color" style="background: #FF9800;"></span>Survived</div>
                        <div class="legend-item"><span class="legend-color" style="background: #FFEB3B;"></span>Weak</div>
                        <div class="legend-item"><span class="legend-color" style="background: #9E9E9E;"></span>Error</div>
                    </div>
                    <div class="graph" id="graph"></div>
                </div>

                <script>
                    const nodesData = $nodes;
                    const linksData = $edges;

                    const width = 1200;
                    const height = 600;

                    const svg = d3.select("#graph")
                        .append("svg")
                        .attr("width", width)
                        .attr("height", height);

                    const simulation = d3.forceSimulation(nodesData)
                        .force("link", d3.forceLink(linksData).id(function(d) { return d.id; }).distance(100))
                        .force("charge", d3.forceManyBody().strength(-200))
                        .force("center", d3.forceCenter(width / 2, height / 2));

                    const link = svg.append("g")
                        .selectAll("line")
                        .data(linksData)
                        .enter().append("line")
                        .attr("class", function(d) { return "link " + d.status; });

                    const node = svg.append("g")
                        .selectAll("circle")
                        .data(nodesData)
                        .enter().append("circle")
                        .attr("class", function(d) { return "node " + d.type + " " + (d.status || ""); })
                        .attr("r", function(d) { return d.type === "target" ? 10 : 8; })
                        .call(d3.drag()
                            .on("start", dragstarted)
                            .on("drag", dragged)
                            .on("end", dragended));

                    const tooltip = d3.select("body").append("div")
                        .attr("class", "tooltip")
                        .style("opacity", 0);

                    node.on("mouseover", function(event, d) {
                        tooltip.transition().duration(200).style("opacity", .9);
                        tooltip.text("");
                        tooltip.append("strong").text(d.id);
                        tooltip.append("br");
                        tooltip.append("text").text("Type: " + d.type);
                        tooltip.append("br");
                        tooltip.append("text").text("Status: " + (d.status || "N/A"));
                        tooltip.style("left", (event.pageX + 10) + "px")
                            .style("top", (event.pageY - 10) + "px");
                    })
                    .on("mouseout", function(d) {
                        tooltip.transition().duration(500).style("opacity", 0);
                    });

                    simulation.on("tick", function() {
                        link.attr("x1", function(d) { return d.source.x; })
                            .attr("y1", function(d) { return d.source.y; })
                            .attr("x2", function(d) { return d.target.x; })
                            .attr("y2", function(d) { return d.target.y; });

                        node.attr("cx", function(d) { return d.x; })
                            .attr("cy", function(d) { return d.y; });
                    });

                    function dragstarted(event) {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        event.subject.fx = event.subject.x;
                        event.subject.fy = event.subject.y;
                    }

                    function dragged(event) {
                        event.subject.fx = event.x;
                        event.subject.fy = event.y;
                    }

                    function dragended(event) {
                        if (!event.active) simulation.alphaTarget(0);
                        event.subject.fx = null;
                        event.subject.fy = null;
                    }
                </script>
            </body>
            </html>
            """.trimIndent()

        outputDir.mkdirs()
        val file = File(outputDir, "mutation-graph.html")
        file.writeText(html)
        return file
    }

    private fun buildNodes(report: MutationReport): String {
        val nodes = mutableListOf<String>()

        // Add target-class nodes (the class the mutation belongs to).
        val targetClasses = report.results.map { it.mutation.className }.distinct()
        for (targetClass in targetClasses) {
            nodes.add("""{"id": "${escapeJson(targetClass)}", "type": "target"}""")
        }

        // Add mutation nodes
        for (result in report.results) {
            val status =
                when {
                    result.isKilled -> "killed"
                    result.isSurvived -> "survived"
                    else -> "error"
                }
            nodes.add("""{"id": "${escapeJson(result.mutation.id)}", "type": "mutation", "status": "$status"}""")
        }

        return "[${nodes.joinToString(",")}]"
    }

    private fun buildEdges(report: MutationReport): String {
        val edges = mutableListOf<String>()

        for (result in report.results) {
            // Map every MutationStatus to a CSS class consumed by the
            // .link.<status> rules in the embedded stylesheet. The
            // mapping mirrors the legend so every bucket has a
            // distinct visual: killed=red, survived=orange (dashed),
            // weak=light-green, error=red (dotted), timeout=orange
            // (densely dotted), no_coverage=gray. Subsumed shares
            // killed's class — it IS a kill, just redundant.
            val status =
                when (result.status) {
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.KILLED -> "killed"
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.SUBSUMED -> "killed"
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.WEAK_KILLED -> "weak"
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.SURVIVED -> "survived"
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.ERROR -> "error"
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.TIMEOUT -> "timeout"
                    com.github.rodrigotimoteo.mutation.model.MutationStatus.NO_COVERAGE -> "no_coverage"
                }
            edges.add(
                """{"source": "${escapeJson(
                    result.mutation.className,
                )}", "target": "${escapeJson(result.mutation.id)}", "status": "$status"}""",
            )
        }

        return "[${edges.joinToString(",")}]"
    }

    private fun escapeJson(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("</", "<\\/")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
