package com.placement.teama.concurrency;

import java.util.*;

/**
 * OS Concept: Directed Wait-For Graph (WFG) cycle detection using DFS 3-Coloring.
 * Node = Process / Student Thread, Directed Edge A -> B = A waits for resource owned by B.
 */
public class WaitForGraph {
    private final Map<String, Set<String>> adjList = new HashMap<>();

    public synchronized void addEdge(String waitingProcess, String holdingProcess) {
        adjList.computeIfAbsent(waitingProcess, k -> new HashSet<>()).add(holdingProcess);
        adjList.computeIfAbsent(holdingProcess, k -> new HashSet<>());
    }

    public synchronized void removeEdge(String waitingProcess, String holdingProcess) {
        if (adjList.containsKey(waitingProcess)) {
            adjList.get(waitingProcess).remove(holdingProcess);
        }
    }

    public synchronized List<String> detectCycle() {
        Map<String, String> color = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (String node : adjList.keySet()) {
            color.put(node, "WHITE");
        }

        for (String node : adjList.keySet()) {
            if ("WHITE".equals(color.get(node))) {
                List<String> cycle = dfs(node, color, parent, new ArrayList<>());
                if (cycle != null && !cycle.isEmpty()) return cycle;
            }
        }
        return Collections.emptyList();
    }

    private List<String> dfs(String u, Map<String, String> color, Map<String, String> parent, List<String> path) {
        color.put(u, "GRAY");
        path.add(u);

        for (String v : adjList.getOrDefault(u, Collections.emptySet())) {
            if ("GRAY".equals(color.get(v))) {
                // Cycle Detected! Reconstruct circular dependency path
                int idx = path.indexOf(v);
                List<String> cyclePath = new ArrayList<>(path.subList(idx, path.size()));
                cyclePath.add(v);
                return cyclePath;
            } else if ("WHITE".equals(color.get(v))) {
                parent.put(v, u);
                List<String> result = dfs(v, color, parent, path);
                if (result != null) return result;
            }
        }
        color.put(u, "BLACK");
        path.remove(path.size() - 1);
        return null;
    }
}