package br.ufpe.cin.if710.podcast.domain;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class ItemFeedTest {

    List<ItemFeed> feedList = new LinkedList<>();

    @Before
    public void initiateList() {
        feedList.add(new ItemFeed(
                "Titulo1",
                "http://www.podcast.com/t1",
                "13/12/2017",
                "Sobre algo 1",
                "http://www.podcast.com/t1/d1"));

        feedList.add(new ItemFeed(
                "Titulo2",
                "http://www.podcast.com/t2",
                "13/12/2017",
                "Sobre algo 2",
                "http://www.podcast.com/t2/d2"));
    }

    @Test
    public void test1() {
        ItemFeed item = feedList.get(0);
        Assert.assertEquals("Ops! Algo deu errado.", "Titulo1", item.toString());
    }

    @Test
    public void test2() {
        ItemFeed item = feedList.get(1);
        Assert.assertEquals("Ops! Algo deu errado.", "http://www.podcast.com/t2", item.getLink());
    }

    @Test
    public void test3() {
        ItemFeed item = feedList.get(0);
        Assert.assertEquals("Ops! Algo deu errado.", "13/12/2017", item.getPubDate());
    }

    @Test
    public void test4() {
        ItemFeed item = feedList.get(1);
        Assert.assertEquals("Ops! Algo deu errado.", "Sobre algo 2", item.getDescription());
    }

    @Test
    public void test5() {
        ItemFeed item = feedList.get(0);
        Assert.assertEquals("Ops! Algo deu errado.", "http://www.podcast.com/t1/d1", item.getDownloadLink());
    }

    @After
    public void clearList() {
        feedList.clear();
    }
}