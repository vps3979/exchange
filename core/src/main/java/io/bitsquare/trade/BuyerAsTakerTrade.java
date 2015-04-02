/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.buyer.BuyerAsTakerProtocol;
import io.bitsquare.trade.states.TakerTradeState;
import io.bitsquare.trade.states.TradeState;

import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerAsTakerTrade extends Trade implements TakerTrade, BuyerTrade, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(BuyerAsTakerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer, Storage<? extends TradeList> storage) {
        super(offer, tradeAmount, tradingPeer, storage);
        log.trace("Created by constructor");
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        initStateProperties();
        initAmountProperty();
    }

    @Override
    protected void initStates() {
        processState = TakerTradeState.ProcessState.UNDEFINED;
        lifeCycleState = TakerTradeState.LifeCycleState.PENDING;
        initStateProperties();
    }

    @Override
    public void createProtocol() {
        tradeProtocol = new BuyerAsTakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        assert tradeProtocol instanceof BuyerAsTakerProtocol;
        ((BuyerAsTakerProtocol) tradeProtocol).takeAvailableOffer();
    }

    @Override
    public void onFiatPaymentStarted() {
        assert tradeProtocol instanceof BuyerAsTakerProtocol;
        ((BuyerAsTakerProtocol) tradeProtocol).onFiatPaymentStarted();
    }

    @Override
    public Coin getPayoutAmount() {
        return getSecurityDeposit().add(getTradeAmount());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setProcessState(TradeState.ProcessState processState) {
        super.setProcessState(processState);

        switch ((TakerTradeState.ProcessState) processState) {
            case EXCEPTION:
                disposeProtocol();
                setLifeCycleState(TakerTradeState.LifeCycleState.FAILED);
                break;
        }
    }

    @Override
    public void setLifeCycleState(TradeState.LifeCycleState lifeCycleState) {
        super.setLifeCycleState(lifeCycleState);

        switch ((TakerTradeState.LifeCycleState) lifeCycleState) {
            case FAILED:
                disposeProtocol();
                break;
            case COMPLETED:
                disposeProtocol();
                break;
        }
    }

    @Override
    public void setThrowable(Throwable throwable) {
        super.setThrowable(throwable);

        setProcessState(TakerTradeState.ProcessState.EXCEPTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handleConfidenceResult() {
        if (((TakerTradeState.ProcessState) processState).ordinal() < TakerTradeState.ProcessState.DEPOSIT_CONFIRMED.ordinal())
            setProcessState(TakerTradeState.ProcessState.DEPOSIT_CONFIRMED);
    }
}