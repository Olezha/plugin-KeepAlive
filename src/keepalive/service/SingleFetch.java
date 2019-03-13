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
package keepalive.service;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClientImpl;
import freenet.keys.FreenetURI;
import freenet.support.compress.Compressor;
import freenet.support.io.ArrayBucket;
import keepalive.Reinserter;
import keepalive.model.Block;

import java.io.IOException;

public class SingleFetch extends SingleJob {

	boolean bPersistenceCheck;

	public SingleFetch(Reinserter reinserter, Block block, boolean bPersistenceCheck) {
		super(reinserter, "fetch", block);

		this.setName("KeepAlive SingleFetch");
		this.bPersistenceCheck = bPersistenceCheck;
	}

	@Override
	public void run() {
		super.run();
        FetchResult fetchResult = null;
		try {

			// init
			//HighLevelSimpleClientImpl hlsc = (HighLevelSimpleClientImpl) plugin.pluginContext.node.clientCore.makeClient((short) 3, false, false);
			HLSCignoreStore hlscIgnoreStore = new HLSCignoreStore(plugin.getFreenetClient());

			FreenetURI fetchUri = block.getUri().clone();
			block.setFetchDone(false);
			block.setFetchSuccessful(false);

			// modify the control flag of the URI to get always the raw data
			byte[] aExtraF = fetchUri.getExtra();
			aExtraF[2] = 0;

			// get the compression algorithm of the block
			String cCompressorF;
			if (aExtraF[4] >= 0) {
				cCompressorF = Compressor.COMPRESSOR_TYPE.getCompressorByMetadataID((short) aExtraF[4]).name;
			} else {
				cCompressorF = "none";
			}

			// request
			try {

				log("request: " + block.getUri().toString() + " (crypt=" + aExtraF[1] + ",control=" + block.getUri().getExtra()[2] + ",compress=" + aExtraF[4] + "=" + cCompressorF + ")", 2);
				if (!bPersistenceCheck) {
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
				}
			}

			//finish
			reinserter.registerBlockFetchSuccess(block);
			block.setFetchDone(true);
			finish();

		} catch (IOException e) {
			plugin.log("SingleFetch.run(): " + e.getMessage(), 0);
		}
        finally{
            if (fetchResult != null && fetchResult.asBucket() != null) {
                fetchResult.asBucket().free();
            }
        }
	}

	private class HLSCignoreStore extends HighLevelSimpleClientImpl {

		public HLSCignoreStore(HighLevelSimpleClientImpl hlsc) {
			super(hlsc);
		}

		@Override
		public FetchContext getFetchContext() {
			FetchContext fc = super.getFetchContext();
			fc.ignoreStore = true;
			return fc;
		}
	}
}
