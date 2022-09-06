package org.matsim.alonso_mora.algorithm.relocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Solves the relocation problem as described by Alonso-Mora et al. using the
 * Cbc solver via file transmission. Cbc must be avaialble on the system to use
 * this solver.
 * 
 * @author sebhoerl
 */
public class CbcMpsRelocationSolver implements RelocationSolver {
	static public final String TYPE = "CbcMps";

	private final static Logger logger = LogManager.getLogger(CbcMpsRelocationSolver.class);

	private final File problemPath;
	private final File solutionPath;

	private final int timeLimit;

	public CbcMpsRelocationSolver(int timeLimit, File problemPath, File solutionPath) {
		this.problemPath = problemPath;
		this.solutionPath = solutionPath;

		this.timeLimit = timeLimit;
	}

	@Override
	public Collection<Relocation> solve(List<Relocation> candidates) {
		try {
			List<Relocation> relocations = new ArrayList<>(candidates);
			new MpsRelocationWriter(relocations).write(problemPath);

			new ProcessBuilder("cbc", problemPath.toString(), "sec", String.valueOf(1e-3 * timeLimit), "solve",
					"solution", solutionPath.toString()).start().waitFor();

			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(solutionPath)));

			String line = null;
			boolean isFirstLine = true;

			boolean isOptimal = true;
			List<Relocation> solution = new LinkedList<>();

			while ((line = reader.readLine()) != null) {
				if (isFirstLine) {
					if (!line.startsWith("Optimal")) {
						isOptimal = false;
						break;
					}

					isFirstLine = false;
				} else if (line.contains("T")) {
					String[] parts = line.trim().split("\\s+");

					if (parts[2].equals("1")) {
						int candidateIndex = Integer.parseInt(parts[1].replace("T", ""));
						solution.add(relocations.get(candidateIndex));
					}
				}
			}

			reader.close();

			if (!isOptimal) {
				logger.warn("Cbc MPS solution is not optimal");
			}

			return solution;
		} catch (FileNotFoundException e) {
			logger.warn("Cbc MPS solver did not finish successfully");
			return Collections.emptySet();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	static public boolean checkAvailability() {
		return CbcMpsRelocationSolver.checkAvailability();
	}
}
