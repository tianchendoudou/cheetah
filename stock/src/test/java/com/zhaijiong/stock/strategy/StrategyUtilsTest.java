package com.zhaijiong.stock.strategy;

import com.google.common.collect.Lists;
import com.zhaijiong.stock.common.Conditions;
import com.zhaijiong.stock.common.DateRange;
import com.zhaijiong.stock.common.StockConstants;
import com.zhaijiong.stock.dao.StockDB;
import com.zhaijiong.stock.model.StockData;
import com.zhaijiong.stock.provider.Provider;
import com.zhaijiong.stock.tools.StockPool;
import com.zhaijiong.stock.tools.ThreadPool;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;
@RunWith(SpringJUnit4ClassRunner.class)  //使用junit4进行测试
@ContextConfiguration({"classpath:applicationContext.xml"}) //加载配置文件
public class StrategyUtilsTest {

    @Autowired
    StockDB stockDB;

    @Test
    public void testAverageBond() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2015,10,13,0,0);
        long time = calendar.getTime().getTime();
        ExecutorService executorService = Executors.newFixedThreadPool(16);

        List<String> stockList = Provider.tradingStockList();
        CountDownLatch countDownLatch = new CountDownLatch(stockList.size());
        for(String stock :stockList){
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    List<StockData> stockDataList = Provider.dailyData(stock,500,false);
                    if(stockDataList.size()>120){
                        stockDataList = Provider.computeMA(stockDataList,"close");
                        stockDataList = StrategyUtils.averageBond(stockDataList,0.005);
                        for(StockData stockData:stockDataList){
                            if(stockData.date.getTime()>time &&
                                    stockData.get(StockConstants.AVERAGE_BOND)>3 &&
                                    stockData.get(StockConstants.CLOSE)>stockData.get(StockConstants.CLOSE_MA10)){
                                System.out.println(stockData);
                                break;
                            }
                        }
                    }
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
    }

    @Test
    public void testGoldenSpider() throws InterruptedException {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2015,10,18,0,0);
        long time = calendar.getTime().getTime();

        ThreadPool.init(16);
        Conditions conditions = new Conditions();
        conditions.addCondition("close", Conditions.Operation.LT,30d);
        conditions.addCondition("PE",Conditions.Operation.LT,200d);
        conditions.addCondition("marketValue", Conditions.Operation.LT, 100d);
        StockPool stockPool = new StockPool();
        List<String> stockList = stockPool.listByConditions(conditions);

        CountDownLatch countDownLatch = new CountDownLatch(stockList.size());
        for(String stock :stockList){
            ThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try{
                        List<StockData> stockDataList = Provider.dailyData(stock, 500, false);
                        stockDataList = StrategyUtils.goldenSpider(stockDataList);
                        for(StockData stockData:stockDataList){
                            if (stockDataList.size() > 120) {
                                if(stockData.date.getTime()>time
                                        && stockData.get(StockConstants.GOLDEN_SPIDER)!=null
                                        && stockData.get(StockConstants.GOLDEN_SPIDER)==1){
                                    System.out.println(stockData);
                                }
                            }
                        }
                    }finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }
        countDownLatch.await();
    }

    @Test
    public void testChange(){
        DateRange dateRange = DateRange.getRange(1000);
        String symbol = "600751";

        List<StockData> stocks = stockDB.getStockDataDaily(symbol, dateRange.start(), dateRange.stop());
        Conditions conditions = new Conditions();
        conditions.addCondition("change", Conditions.Operation.LT,-8d);
        System.out.println(StrategyUtils.change(stocks,21,conditions));
    }
}