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

import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.support.compress.Compressor;
import keepalive.Reinserter;
import keepalive.model.Block;
import keepalive.model.Segment;

public class SingleInsert extends SingleJob {

	public SingleInsert(Reinserter reinserter, Block block) {
		super(reinserter, "insertion", block);
		this.setName("KeepAlive SingleInsert");
	}

	@Override
	public String toString() {
		return "KeepAlive - SingleInsert";
	}

	@Override
	public void run() {
		super.run();
		try {

			//HighLevelSimpleClientImpl hlsc = (HighLevelSimpleClientImpl) plugin.pluginContext.hlsc;
			FreenetURI fetchUri = block.getUri().clone();
			block.setInsertDone(false);
			block.setInsertSuccessful(false);

			// modify the control flag of the URI to get always the raw data
			byte[] aExtraI = fetchUri.getExtra();
			aExtraI[2] = 0;

			// get the compression algorithm of the block
			String cCompressorI;
			if (aExtraI[4] >= 0) {
				cCompressorI = Compressor.COMPRESSOR_TYPE.getCompressorByMetadataID((short) aExtraI[4]).name;
			} else {
				cCompressorI = "none";
			}

			// fetch
			if (block.getBucket() == null) {
				SingleFetch singleFetch = new SingleFetch(reinserter, block, false);
				singleFetch.start();
				singleFetch.join();
				if (!reinserter.isActive()) {
					return;
				}
			}

			Segment segment = reinserter.getSegments().get(block.getSegmentId());
			// insert
			if (block.getBucket() != null) {
				FreenetURI insertUri;

				try {
					InsertBlock insertBlock = new InsertBlock(block.getBucket(), null, fetchUri);
					InsertContext insertContext = plugin.getFreenetClient().getInsertContext(true);
					if (cCompressorI != null && !cCompressorI.equals("none")) {
						insertContext.compressorDescriptor = cCompressorI;
					}
					if (aExtraI[1] == 2) // switch to crypto_algorithm  2 (instead of using the new one that is introduced since 1416)
					{
						insertContext.setCompatibilityMode(InsertContext.CompatibilityMode.COMPAT_1255);
					}
					//don't triple-insert blocks.
					insertContext.extraInsertsSingleBlock = 0;
					insertContext.earlyEncode = false;

					//re-insert top blocks and single key files at very high priority, all others at medium prio.
					short prio = segment.size() == 1 ? (short) 1 : (short) 3;

					insertUri = plugin.getFreenetClient().insert(insertBlock, null, false, prio, insertContext, fetchUri.getCryptoKey());

					// insert finished
					if (!reinserter.isActive()) {
						return;
					}
					int nSiteId = plugin.getIntProp("reinserter_site_id");
					if (insertUri != null) {
						if (fetchUri.equals(insertUri)) {
							block.setInsertSuccessful(true);
							block.setResultLog("-> inserted: " + insertUri.toString());
						} else {
							block.setResultLog("-> insertion failed - different uri: " + insertUri.toString());
						}
					} else {
						block.setResultLog("-> insertion failed");
					}

				} catch (InsertException e) {
					block.setResultLog("-> insertion error: " + e.getMessage());
				}

			} else {
				block.setResultLog("-> insertion failed: fetch failed");
			}

			// reg success if single-block-segment
			if (segment.size() == 1) {
				reinserter.updateSegmentStatistic(segment, block.getInsertSuccessful());
			}

			// finish
			block.setInsertDone(true);
			finish();

		} catch (Exception e) {
			plugin.log("SingleInsert.run(): " + e.getMessage(), 0);
		}
	}
}
