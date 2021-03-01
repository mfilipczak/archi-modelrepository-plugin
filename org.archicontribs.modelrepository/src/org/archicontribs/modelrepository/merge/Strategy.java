package org.archicontribs.modelrepository.merge;
import org.eclipse.jgit.merge.MergeStrategy;

public enum Strategy {
	OURS(MergeStrategy.OURS),
	THEIRS(MergeStrategy.THEIRS);
	
	private final MergeStrategy strategy;
	
	private Strategy(MergeStrategy strategy) {
		this.strategy = strategy;
	}
	
	public MergeStrategy getStrategy() {
		return strategy;
	}
	
	public static Strategy getStrategyByName(String strategy) {
		for(Strategy s : Strategy.values()) {
			if(s.name().equalsIgnoreCase(strategy)) {
				return s;
			}
		}
		return null;
	}
	
}
