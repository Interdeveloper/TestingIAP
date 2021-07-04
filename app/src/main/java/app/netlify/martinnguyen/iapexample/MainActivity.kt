package app.netlify.martinnguyen.iapexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import app.netlify.martinnguyen.iapexample.databinding.ActivityMainBinding
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener, BillingClientStateListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var billingClient: BillingClient
    private var skuDetails: List<SkuDetails>? = null
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO ok to call this in onCreate()?
        setupBillingClient()

        viewModel.isPremium.observe(this) { isPremium ->
            if (isPremium) {
                binding.subtitleTextView.text = "PREMIUM"
            } else {
                binding.subtitleTextView.text = "NOT PREMIUM"
            }
        }

        binding.buyButton.setOnClickListener{
            purchasePremium()
        }
    }

    // TODO should we be trying to verify purchases in onResume()? Google recommended this.
    override fun onResume() {
        super.onResume()
        // I don't think it's safe to assume billingClient exists for us to use in onResume
        if (billingClient.isReady) {
            queryPurchases()
        }
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener(this).build()

        connectToBillingService()
    }

    private fun connectToBillingService() {
        if (!billingClient.isReady) {
            billingClient.startConnection(this)
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // get all available products for purchase
            querySkuDetails()

            // get purchases that user has made
            queryPurchases()
        }
    }

    override fun onBillingServiceDisconnected() {
        connectToBillingService()
    }

    private fun querySkuDetails() {
        val skuList = ArrayList<String>()
        skuList.add("premium_upgrade")

        val params = SkuDetailsParams
            .newBuilder()
            .setSkusList(skuList)
            .setType(BillingClient.SkuType.INAPP)
            .build()

        billingClient.querySkuDetailsAsync(
            params
        ) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                skuDetails = skuDetailsList
            }
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                // TODO when sort of response code should we be checking for items already purchased?
                entitleUserProducts()
            }
        }
    }

    private fun purchasePremium() {
        // just grab the first sku in the list and assume it is for premium
        if (skuDetails != null && skuDetails!!.size == 1) {
            val skuDetail = skuDetails!![0]

            val params = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetail)
                .build()

            billingClient.launchBillingFlow(this, params)
                .takeIf { billingResult -> billingResult.responseCode != BillingClient.BillingResponseCode.OK }
                ?.let { billingResult ->
                    Log.e("BillingClient", "Failed to launch billing flow $billingResult")
                }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.apply { processPurchases(this.toSet()) }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // call queryPurchases to verify and process all owned items
                queryPurchases()
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                connectToBillingService()
            }
            else -> {
                Log.e("BillingClient", "Failed to onPurchasesUpdated")
            }
        }
    }

    private fun processPurchases(purchases: Set<Purchase>) {
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    acknowledge(purchase.purchaseToken)
                }
            }
        }
    }

    private fun acknowledge(purchaseToken: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(
            acknowledgePurchaseParams
        ) { billingResult ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    entitleUserProducts()
                }
                else -> {
                    Log.e("BillingClient", "Failed to acknowledge purchase $billingResult")
                }
            }
        }
    }

    // TODO is it okay to just store if the user is premium in some variable? Then have the whole app check against this variable to unlock features? (do not show ads if user is premium etc)
    private fun entitleUserProducts() {
        viewModel.setIsPremium(true)
    }
}