package com.rhb.gulex.simulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rhb.gulex.bluechip.service.BluechipService;
import com.rhb.gulex.simulation.api.OnHandView;
import com.rhb.gulex.simulation.api.TradeRecordView;
import com.rhb.gulex.simulation.api.ValueView;
import com.rhb.gulex.stock.service.StockService;
import com.rhb.gulex.traderecord.repository.TradeRecordEntity;
import com.rhb.gulex.traderecord.repository.TradeRecordRepository;
import com.rhb.gulex.traderecord.service.TradeRecordService;

/**
 * 
 * @author ran
 * 买卖操作模拟器
 * 每天收盘后执行一次
 *
 *
 */

@Service("SimulationServiceImp")
public class SimulationServiceImp implements SimulationService {
	@Value("${dataPath}")
	private String dataPath;
	
	@Autowired
	@Qualifier("StockServiceImp")
	StockService stockService;
	
	@Autowired
	@Qualifier("TradeRecordRepositoryImpFrom163")
	TradeRecordRepository tradeRecordRepository;
	
	@Autowired
	@Qualifier("TradeRecordServiceImp")
	TradeRecordService tradeRecordService;
	
	@Autowired
	@Qualifier("BluechipServiceImp")
	BluechipService bluechipService;
	
	
	private  BigDecimal amount = new BigDecimal(50000); //每次买入金额,默认金额为5万
	private Integer upProbability = 60;   //买入阈值，默认60.    实际操作时，行情好时，阈值低一些，行情不好时，阈值高些
	private BigDecimal cash = new BigDecimal(1000000); //初始现金,默认为1百万
	private LocalDate beginDate = LocalDate.parse("2010-01-01");  //能找到的年报发布日期是从2010年开始的
	private boolean overdraft = true;  //是否允许透支
	
	private Trader trader = null;
	
	private Collection<TradeDetail> onHands;
	private Iterator<TradeDetail> it;
	private TradeDetail detail;
	private TradeRecordEntity tradeRecordEntity;
	
	
	@Override
	public void init(){
		System.out.println("SimulationService init begin......");
		trader = new Trader();
		trader.setAmount(amount); 
		trader.setCash(cash); 
		trader.setOverdraft(overdraft);
		
		List<LocalDate> tradeDates = this.getTradeDate(beginDate);
		
		int i=0;
		for(LocalDate sDate : tradeDates){
			System.out.print(i++ + "/" + tradeDates.size() + "\r");
			
			trade(sDate);
			
		}
		System.out.println("there are " + tradeDates.size() + " trade dates happened.");
		System.out.println(".......SimulationService init end......");
	}
	
	
	
	public LocalDate getBeginDate() {
		return beginDate;
	}



	public void setBeginDate(LocalDate beginDate) {
		this.beginDate = beginDate;
	}



	@Override
	public List<TradeRecordView> getTradeRecordViews() {
		if(trader == null){
			this.init();
		}
		List<TradeRecordView> views = new ArrayList<TradeRecordView>();
		List<TradeDetail> details = this.trader.getDetails();
		for(TradeDetail detail : details){
			TradeRecordView view = new TradeRecordView();
			view.setDate(detail.getTradeDate().toString());
			view.setStockname(detail.getName());
			view.setQuantity(detail.getQuantity());
			if(detail.getQuantity()>0){
				view.setBuyorsell("买入");
				view.setPrice(detail.getCost());
			}else{
				view.setBuyorsell("卖出");
				view.setPrice(detail.getPrice());
			}
			views.add(view);
		}
			
		return views;
	}

	@Override
	public List<OnHandView> getOnHandViews() {
		if(trader == null){
			this.init();
		}
		
		List<OnHandView> views = new ArrayList<OnHandView>();
		List<TradeDetail> details = trader.getOnHands();
		for(TradeDetail detail : details){
			OnHandView view = new OnHandView();
			view.setCode(detail.getCode());
			view.setTradeDate(detail.getTradeDate().toString());
			view.setName(detail.getName());
			view.setQuantity(detail.getQuantity());
			view.setCost(detail.getCost());
			view.setPrice(detail.getPrice());
			views.add(view);
		}
		
		return views;
	}

	@Override
	public List<ValueView> getValueViews() {
		if(trader == null){
			this.init();
		}
		List<ValueView> views = new ArrayList<ValueView>();
		List<Account> accounts = trader.getAccounts();
		for(Account account : accounts) {
			ValueView view = new ValueView();
			view.setDate(account.getDate().toString());
			view.setValue(account.getValue());
			view.setCash(account.getCash());
			views.add(view);
		}
		
		return views;
	}
	

	public BigDecimal getAmount() {
		return amount;
	}


	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}


	public Integer getUpProbability() {
		return upProbability;
	}


	public void setUpProbability(Integer upProbability) {
		this.upProbability = upProbability;
	}
	
	private List<LocalDate> getTradeDate(LocalDate beginDate){
		String code = "sh000001"; //以上证指数的交易日期作为历史交易日历
		List<LocalDate> codes = new ArrayList<LocalDate>();
		List<TradeRecordEntity> records = tradeRecordRepository.getTradeRecordEntities(code);
		for(TradeRecordEntity record : records){
			if(record.getDate().isAfter(beginDate)){
				codes.add(record.getDate());
			}
		}
		return codes;
	}

	@Override
	public void trade(LocalDate sDate) {
		List<BluechipDto> bluechips = bluechipService.getBluechips(sDate);
		
		//买入操作
		for(BluechipDto bluechipDto : bluechips){
			
			tradeRecordEntity = tradeRecordService.getTradeRecordEntity(bluechipDto.getCode(),sDate);
			
			if(tradeRecordEntity!=null  
				&& tradeRecordEntity.getUpProbability()>upProbability 
				&& tradeRecordEntity.isPriceOnAvarage()   
				&& ChronoUnit.DAYS.between(LocalDate.parse(bluechipDto.getIpoDate()), sDate)>365   //上市1年后才能购买
				){ 

				if(!trader.onHand(bluechipDto.getCode())){ 
					trader.buy(bluechipDto.getCode(),sDate,tradeRecordEntity.getPrice(),bluechipDto.getName());

				}
			}
		}

		//卖出操作：卖出低于120日 以上均线的票。最后一天全卖出
		onHands = trader.getOnHands();
		it = onHands.iterator();
		while(it.hasNext()){
			detail = it.next();
			tradeRecordEntity = tradeRecordService.getTradeRecordEntity(detail.getCode(),sDate);
			boolean inGoodPeriod = bluechipService.inGoodPeriod(detail.getCode(),sDate);

			
			if(tradeRecordEntity==null){
				System.out.println("tradeRecordService.getTradeRecordEntity(" + detail.getCode() + "," + sDate + ") = null ");
			}
			
			if(!inGoodPeriod && !tradeRecordEntity.isPriceOnAvarage()){
				trader.sell(detail.getCode(), sDate, tradeRecordEntity.getPrice(),bluechipService.getBluechips(detail.getCode()).getName());
			}else{
				trader.setPrice(detail.getCode(), tradeRecordEntity.getPrice());
			}
		}


		trader.dayReport(sDate);		
	}
	
	public void setOverdraft(boolean flag){
		this.overdraft = flag;
	}

}