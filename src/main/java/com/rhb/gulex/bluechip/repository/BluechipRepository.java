package com.rhb.gulex.bluechip.repository;

import java.util.Collection;
import java.util.Set;

public interface BluechipRepository {
	public void save(Collection<BluechipEntity> bluechips);
	public Set<BluechipEntity> getBluechips();
	
}
