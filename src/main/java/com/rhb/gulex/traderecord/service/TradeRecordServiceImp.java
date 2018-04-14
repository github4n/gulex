package com.rhb.gulex.traderecord.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.rhb.gulex.bluechip.repository.BluechipEntity;
import com.rhb.gulex.bluechip.repository.BluechipRepository;
import com.rhb.gulex.bluechip.service.BluechipService;
import com.rhb.gulex.simulation.service.BluechipDto;
import com.rhb.gulex.traderecord.api.TradeRecordDzh;
import com.rhb.gulex.traderecord.repository.TradeRecordEntity;
import com.rhb.gulex.traderecord.repository.TradeRecordRepository;

@Service("TradeRecordServiceImp")
public class TradeRecordServiceImp implements TradeRecordService {

	@Autowired
	@Qualifier("TradeRecordRepositoryImpFromDzh")
	TradeRecordRepository tradeRecordRepositoryFromDzh;

	@Autowired
	@Qualifier("TradeRecordRepositoryFromQt")
	TradeRecordRepository tradeRecordRepositoryFromQt;
/*	
	@Autowired
	@Qualifier("TradeRecordRepositoryImpFrom163")
	TradeRecordRepository tradeRecordRepositoryFrom163;*/
	
	@Autowired
	@Qualifier("BluechipRepositoryImp")
	BluechipRepository bluechipRepository;
	
	@Autowired
	@Qualifier("BluechipServiceImp")
	BluechipService bluechipService;
	
	
	Map<String,TradeRecordDTO> tradeRecordDtos = new HashMap<String,TradeRecordDTO>(); //stockcode - TradeRecordDTO

	private void init(String stockcode){
		List<TradeRecordEntity> entities = new ArrayList<TradeRecordEntity>();
		List<TradeRecordEntity> dzh = tradeRecordRepositoryFromDzh.getTradeRecordEntities(stockcode);
		if(dzh != null && dzh.size()>0) {
			entities.addAll(dzh);
		}
		
		if(stockcode.equals("sh000001")) {
			System.out.println("last date in dzh is " + entities.get(entities.size()-1).getDate());
		}
		
		if(entities.size()>0) {
			List<TradeRecordEntity> e163 = tradeRecordRepositoryFromQt.getTradeRecordEntities(stockcode,entities.get(entities.size()-1).getDate()); 
			entities.addAll(e163);
		}else {
			List<TradeRecordEntity> e163 = tradeRecordRepositoryFromQt.getTradeRecordEntities(stockcode); 
			entities.addAll(e163);
		}
		
		if(stockcode.equals("sh000001")) {
			System.out.println("last date in qt is " + entities.get(entities.size()-1).getDate());
		}
		
		TradeRecordDTO dto = new TradeRecordDTO();
		TradeRecordEntity entity;
		for(int i=0; i<entities.size(); i++) {
			entity = entities.get(i);
			entity.setAv120(calAvaragePrice(entities.subList(0, i),entity.getPrice(),120));
			entity.setAv60(calAvaragePrice(entities.subList(0, i),entity.getPrice(),60));
			entity.setAv30(calAvaragePrice(entities.subList(0, i),entity.getPrice(),30));
			entity.setAboveAv120Days(calAboveAv120Days(entities.subList(0, i)));
			entity.setMidPrice(calMidPrice(entities.subList(0, i),entity.getPrice()));

			dto.add(entity.getDate(), entity);
		}

		//System.out.println("trade records after change");
		//System.out.println(entities.size());
		
		tradeRecordDtos.put(stockcode, dto);
	}

	
	private BigDecimal calAvaragePrice(List<TradeRecordEntity> records, BigDecimal price, Integer days){
		
		BigDecimal total = price;
		int start = records.size()>days ? records.size()-days : 0;
		List<TradeRecordEntity> list = records.subList(start, records.size());
		for(TradeRecordEntity tr : list){
			total = total.add(tr.getPrice());
		}
		//System.out.println("total=" + total);
		int i = records.size() - start + 1;
		return total.divide(new BigDecimal(i),2,BigDecimal.ROUND_HALF_UP);
	}
	
	private Integer calAboveAv120Days(List<TradeRecordEntity> records){
		int above = 0;
		int start = records.size()>100 ? records.size()-100 : 0;
		List<TradeRecordEntity> list = records.subList(start, records.size());
		for(TradeRecordEntity tr : list){
			if(tr.isPriceOnAv(120)){
				above++;
			}
		}
		return above;
	}
	
	private BigDecimal calMidPrice(List<TradeRecordEntity> records,BigDecimal price){
		Set<BigDecimal> prices = new HashSet<BigDecimal>();
		prices.add(price);
		for(TradeRecordEntity entity : records){
			prices.add(entity.getPrice());
		}
		
		List<BigDecimal> list = new ArrayList<BigDecimal>(prices);
		
		Collections.sort(list);
		
		return list.get(prices.size()/2);
	}

	@Override
	public List<TradeRecordEntity> getTradeRecords(String stockcode, LocalDate endDate) {
		if(tradeRecordDtos==null || !tradeRecordDtos.containsKey(stockcode)){
			init(stockcode);
		}
		List<TradeRecordEntity> list = new ArrayList<TradeRecordEntity>();
		List<TradeRecordEntity> entities = tradeRecordDtos.get(stockcode).getTradeRecordEntities();
		for(TradeRecordEntity entity : entities) {
			if(entity.getDate().isBefore(endDate) || entity.getDate().isEqual(endDate)) {
				list.add(entity);
			}
		}
		
		return list;
	}
	
	@Override
	public TradeRecordDTO getTradeRecordsDTO(String stockcode) {
		if(tradeRecordDtos==null || !tradeRecordDtos.containsKey(stockcode)){
			init(stockcode);
		}
		return tradeRecordDtos.get(stockcode);
	}
	
	@Override
	public LocalDate getIpoDate(String stockcode) {
		if(tradeRecordDtos==null || !tradeRecordDtos.containsKey(stockcode)){
			init(stockcode);
		}
		
		TradeRecordDTO dto = this.getTradeRecordsDTO(stockcode);

		if(dto == null){
			System.out.println(stockcode + "还未有成交记录! 请事先准备好！");
			return null;
		}else{
			return dto.getIpoDate();
		}
		
	}


	@Override
	public TradeRecordEntity getTradeRecordEntity(String stockcode, LocalDate date) {
		if(tradeRecordDtos==null || !tradeRecordDtos.containsKey(stockcode)){
			init(stockcode);
		}
		
		TradeRecordDTO dto = this.getTradeRecordsDTO(stockcode);
		if(dto==null) {
			return null;
		}
		return dto.getSimilarTradeRecordEntity(date);
	}

	@Override
	public void setTradeRecordEntity(String stockcode,LocalDate date, BigDecimal price) {
		if(tradeRecordDtos==null || !tradeRecordDtos.containsKey(stockcode)){
			init(stockcode);
		}
		
		TradeRecordDTO dto = this.getTradeRecordsDTO(stockcode);
		if(dto==null) {
			System.out.println(stockcode + "还未有成交记录! 请事先准备好！");
			
		}else {
			TradeRecordEntity entity = dto.getTradeRecordEntity(date);
			
			if(entity != null) {
				entity.setPrice(price);
			}else {
				entity = new TradeRecordEntity();
				entity.setDate(date);
				entity.setPrice(price);
				dto.add(date, entity);
			}

			
			Map<String,Object> total119 = dto.getTotalOf119(date);
			Integer quantity = (Integer)total119.get("quantity");
			BigDecimal total = (BigDecimal)total119.get("total");
			entity.setAv120(total.add(price).divide(new BigDecimal(quantity+1),2,BigDecimal.ROUND_HALF_UP));
			
			entity.setMidPrice(dto.getMidPrice(date, price));
			
			Integer i = entity.isPriceOnAv(120) ? 1 : 0;
			entity.setAboveAv120Days(dto.getAboveAv120Days(date) + i);
		}
	}

	@Override
	public List<TradeRecordDzh> getDzhs() {
		Map<String,TradeRecordDzh> tradeRecordDzhs = new HashMap<String,TradeRecordDzh>();
		TradeRecordDzh tradeRecordDzh;
		
		List<TradeRecordEntity> tradeRecordEntities;
		TradeRecordEntity tradeRecordEntity;
		
		Set<BluechipEntity> bluechipEntities = bluechipRepository.getBluechips();
		for(BluechipEntity bluechipEntity : bluechipEntities){
			tradeRecordEntities = tradeRecordRepositoryFromDzh.getTradeRecordEntities(bluechipEntity.getCode());
			tradeRecordDzh = new TradeRecordDzh();
			tradeRecordDzh.setCode(bluechipEntity.getCode());
			tradeRecordDzh.setName(bluechipEntity.getName());

			if(tradeRecordEntities==null || tradeRecordEntities.size()==0) {
				tradeRecordDzh.setDzhDate("");
				
				//System.out.println(tradeRecordDzh);
			}else {
				tradeRecordEntity = tradeRecordEntities.get(tradeRecordEntities.size()-1);
				tradeRecordDzh.setDzhDate(tradeRecordEntity.getDate().toString());
			}
			
			tradeRecordDzhs.put(tradeRecordDzh.getCode(),tradeRecordDzh);
			
		}
		
/*		List<BluechipDto> bluechipDtos = bluechipService.getBluechips(LocalDate.now());
		for(BluechipDto bluechipDto : bluechipDtos) {
			if(!tradeRecordDzhs.containsKey(bluechipDto)) {
				tradeRecordEntities = tradeRecordRepositoryFromDzh.getTradeRecordEntities(bluechipDto.getCode());
				if(tradeRecordEntities!=null && tradeRecordEntities.size()>=0) {
					tradeRecordEntity = tradeRecordEntities.get(tradeRecordEntities.size()-1);
					tradeRecordDzh = new TradeRecordDzh();
					tradeRecordDzh.setCode(bluechipDto.getCode());
					tradeRecordDzh.setName(bluechipDto.getName());
					tradeRecordDzh.setDzhDate(tradeRecordEntity.getDate().toString());
					
					tradeRecordDzhs.put(tradeRecordDzh.getCode(),tradeRecordDzh);
					
					//System.out.println(tradeRecordDzh);

				}
			}
		}*/
		
		List<TradeRecordDzh> list = new ArrayList<TradeRecordDzh>(tradeRecordDzhs.values());
		Collections.sort(list,new Comparator<TradeRecordDzh>() {

			@Override
			public int compare(TradeRecordDzh o1, TradeRecordDzh o2) {
				return o1.getDzhDate().compareTo(o2.getDzhDate());
			}
			
		});

		return list;
	}

	@Override
	public void refresh() {
		System.out.println("TradeRecordService refresh begin......");
		Set<String> codes = tradeRecordDtos.keySet();
		codes.add("sh000001");
		int i=0;
		for(String code : codes) {
			System.out.print(i++ + "/" + codes.size() + "\r");
			this.init(code);
		}
		System.out.println("there are " + i + " stocks' trade records inited.");
		
		System.out.println(".........TradeRecordService refresh end.");
	}


	@Override
	public List<LocalDate> getTradeDate(LocalDate beginDate) {
		String stockcode = "sh000001"; //以上证指数的交易日期作为历史交易日历
		
		if(tradeRecordDtos==null || !tradeRecordDtos.containsKey(stockcode)){
			init(stockcode);
		}
		
		TradeRecordDTO dto = this.getTradeRecordsDTO(stockcode);

		List<LocalDate> dates = dto.getDates(beginDate);
		
		return dates;
	}

	
}
