package piuk.blockchain.android.ui.buysell.overview

import android.support.annotation.StringRes
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.buysell.overview.models.BuySellButtons
import piuk.blockchain.android.ui.buysell.overview.models.BuySellDisplayable
import piuk.blockchain.android.ui.buysell.overview.models.BuySellTransaction
import piuk.blockchain.android.ui.buysell.overview.models.EmptyTransactionList
import piuk.blockchain.android.ui.buysell.overview.models.KycInProgress
import piuk.blockchain.android.util.extensions.addToCompositeDisposable
import piuk.blockchain.androidbuysell.datamanagers.CoinifyDataManager
import piuk.blockchain.androidbuysell.services.ExchangeService
import piuk.blockchain.androidbuysell.utils.fromIso8601
import piuk.blockchain.androidcore.data.currency.CurrencyFormatManager
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.utils.extensions.applySchedulers
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import timber.log.Timber
import javax.inject.Inject

class CoinifyOverviewPresenter @Inject constructor(
        private val exchangeService: ExchangeService,
        private val coinifyDataManager: CoinifyDataManager,
        private val metadataManager: MetadataManager,
        private val formatManager: CurrencyFormatManager
) : BasePresenter<CoinifyOverviewView>() {

    private val buttons = BuySellButtons()
    private val kycInReview = KycInProgress()

    override fun onViewReady() {
        renderTrades(emptyList())
        view.renderViewState(OverViewState.Loading)
        updateTransactionList()
        // TODO: Compare metadata trades with coinify trades; if order ID is missing, add to metadata
    }

    private fun checkKycStatus() {
        // TODO: Get if KYC is in progress, display KYC card

    }

    internal fun updateTransactionList() {
        exchangeService.getExchangeMetaData()
                .addToCompositeDisposable(this)
                .applySchedulers()
                .map { it.coinify!!.token }
                .flatMap { coinifyDataManager.getTrades(it) }
                .map {
                    BuySellTransaction(
                            transactionId = it.id,
                            time = it.createTime.fromIso8601()!!,
                            inCurrency = it.inCurrency,
                            outCurrency = it.outCurrency,
                            inAmount = it.inAmount.toString(),
                            outAmount = it.outAmount.toString(),
                            tradeState = it.state
                    )
                }
                .toList()
                .doOnError { Timber.e(it) }
                .subscribeBy(
                        onSuccess = { renderTrades(it) },
                        onError = {
                            view.renderViewState(OverViewState.Failure(R.string.buy_sell_overview_error_loading_transactions))
                        }
                )
    }

    private fun renderTrades(trades: List<BuySellTransaction>) {
        val displayList: List<BuySellDisplayable> =
                mutableListOf<BuySellDisplayable>(buttons)
                        .apply { addAll(trades) }
                        .apply { if (trades.isEmpty()) add(EmptyTransactionList()) }
        view.renderViewState(OverViewState.Data(displayList.toList()))
    }
}

sealed class OverViewState {

    object Loading : OverViewState()
    class Failure(@StringRes val message: Int) : OverViewState()
    class Data(val items: List<BuySellDisplayable>) : OverViewState()

}