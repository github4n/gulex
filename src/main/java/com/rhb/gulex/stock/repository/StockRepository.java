package com.rhb.gulex.stock.repository;

import java.util.List;
import java.util.Set;

import com.rhb.gulex.bluechip.repository.BluechipEntity;


public interface StockRepository {
	public String[] getStockCodes();
	public Set<StockEntity> getStocks();
	public void save(Set<StockEntity> stocks);
	
	public void SaveBluechip(List<BluechipEntity> list);
	public List<BluechipEntity> getBluechips();
}
