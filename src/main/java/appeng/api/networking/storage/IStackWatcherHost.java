/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.networking.storage;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

public interface IStackWatcherHost
{
	/**
	 * provides the IStackWatcher for this host, for the current network, is called when the hot changes networks. You
	 * do not need to clear your old watcher, its already been removed by the time this gets called.
	 *
	 * @param newWatcher stack watcher
	 */
	void updateWatcher(IStackWatcher newWatcher);

	/**
	 * Called when a watched item changes amounts.
	 *
	 * @param o         changed item list
	 * @param fullStack old stack
	 * @param diffStack new stack
	 * @param src       action source
	 * @param chan      storage channel
	 */
	void onStackChange(IItemList o, IAEStack fullStack, IAEStack diffStack, BaseActionSource src, StorageChannel chan);

	// TODO gamerforEA code start

	/**
	 * Called when a watched item changes amounts.
	 *
	 * @param o         changed item list
	 * @param fullStack old stack
	 * @param diffStack new stack
	 * @param src       action source
	 * @param chan      storage channel
	 * @return network storage list is dirty
	 */
	default boolean onStackChangeAdv(IItemList o, IAEStack fullStack, IAEStack diffStack, BaseActionSource src, StorageChannel chan)
	{
		this.onStackChange(o, fullStack, diffStack, src, chan);
		return true;
	}
	// TODO gamerforEA code end
}