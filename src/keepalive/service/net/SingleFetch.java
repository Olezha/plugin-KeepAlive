/*
 * Keep Alive Plugin
 * Copyright (C) 2012 Jeriadoc
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package keepalive.service.net;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClientImpl;
import freenet.keys.FreenetURI;
import freenet.support.io.ArrayBucket;
import keepalive.service.reinserter.Reinserter;
import keepalive.model.Block;

import java.io.IOException;
import java.util.concurrent.Callable;

public class SingleFetch extends SingleJob implements Callable<Boolean> {

    private boolean persistenceCheck;

    public SingleFetch(Reinserter reinserter, Block block, boolean persistenceCheck) {
        super(reinserter, "fetch", block);

        this.persistenceCheck = persistenceCheck;
    }

    @Override
    public Boolean call() {
        Thread.currentThread().setName("KeepAlive SingleFetch");
        FetchResult fetchResult = null;
        boolean fetchSuccessful = false;

        try {

            // init
            HLSCIgnoreStore hlscIgnoreStore = HLSCIgnoreStore.getInstance(plugin.getFreenetClient());

            FreenetURI fetchUri = getUri();
            block.setFetchDone(false);
            block.setFetchSuccessful(false);

            // request
            try {

                if (!persistenceCheck) {
                    fetchResult = plugin.getFreenetClient().fetch(fetchUri);
                } else {
                    fetchResult = hlscIgnoreStore.fetch(fetchUri);
                }

            } catch (FetchException e) {
                block.setResultLog("-> fetch error: " + e.getMessage());
            }

            // log / success flag
            if (block.getResultLog() == null) {
                if (fetchResult == null) {
                    block.setResultLog("-> fetch failed");
                } else {
                    block.setBucket(new ArrayBucket(fetchResult.asByteArray()));
                    block.setFetchSuccessful(true);
                    block.setResultLog("-> fetch successful");
                    fetchSuccessful = true;
                }
            }

            //finish
            reinserter.registerBlockFetchSuccess(block);
            block.setFetchDone(true);

        } catch (IOException e) {
            log("SingleFetch.run(): " + e.getMessage(), 0);
        } finally {
            if (fetchResult != null && fetchResult.asBucket() != null) {
                fetchResult.asBucket().free();
            }
            finish();
        }

        return fetchSuccessful;
    }
}

class HLSCIgnoreStore extends HighLevelSimpleClientImpl {

    private static volatile HLSCIgnoreStore hlscIgnoreStore;

    private HLSCIgnoreStore(HighLevelSimpleClientImpl hlsc) {
        super(hlsc);
    }

    static HLSCIgnoreStore getInstance(HighLevelSimpleClientImpl hlsc) {
        HLSCIgnoreStore localHlscIgnoreStore = hlscIgnoreStore;
        if (localHlscIgnoreStore == null) {
            synchronized (HLSCIgnoreStore.class) {
                localHlscIgnoreStore = hlscIgnoreStore;
                if (localHlscIgnoreStore == null) {
                    hlscIgnoreStore = localHlscIgnoreStore = new HLSCIgnoreStore(hlsc);
                }
            }
        }
        return localHlscIgnoreStore;
    }

    @Override
    public FetchContext getFetchContext() {
        FetchContext fc = super.getFetchContext();
        fc.ignoreStore = true;
        return fc;
    }
}
