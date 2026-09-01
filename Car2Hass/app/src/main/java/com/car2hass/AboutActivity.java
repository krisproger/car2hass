package com.car2hass;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends BaseLocalizedActivity {

    private static final String PROJECT_URL = "https://mytechnic.ru/cartelemetry/";
    private static final String TELEGRAM_URL = "https://t.me/bydiplus2hass";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new CrashLogger(this).register();
        try {
            setContentView(R.layout.activity_about);

            ((TextView) findViewById(R.id.tvVersion)).setText(getString(R.string.about_version, AppInfo.getVersionString(this)));

            safeSetup("siteLink", () -> setupLink(R.id.tvSite, PROJECT_URL));
            safeSetup("telegramLink", () -> setupLink(R.id.tvTelegram, TELEGRAM_URL));

            // Collapsible donation sections
            safeSetup("banksSpoiler", () -> setupSpoiler(findViewById(R.id.tvBanksTitle), findViewById(R.id.layoutBanks)));
            safeSetup("cryptoSpoiler", () -> setupSpoiler(findViewById(R.id.tvCryptoTitle), findViewById(R.id.layoutCrypto)));

            // Payment aggregator
            safeSetup("donateCloudtips", () -> setupDonateLink(R.id.donateCloudtips, getString(R.string.donate_cloudtips),
                    "https://pay.cloudtips.ru/p/0ef6b51b", R.drawable.qr_cloudtips));

            // Bank cards
            safeSetup("donateTbank", () -> setupDonateRow(R.id.donateTbank, getString(R.string.donate_tbank),
                    "2200 7006 1069 1486", 0));
            safeSetup("donateAlfa", () -> setupDonateRow(R.id.donateAlfa, getString(R.string.donate_alfa),
                    "2200 1523 9377 1947", 0));
            safeSetup("donateSber", () -> setupDonateRow(R.id.donateSber, getString(R.string.donate_sber),
                    "2202 2032 6583 9417", 0));
            safeSetup("donateVtb", () -> setupDonateRow(R.id.donateVtb, getString(R.string.donate_vtb),
                    "2200 2414 5379 1539", 0));

            // Crypto
            safeSetup("donateUsdtTrc20", () -> setupDonateRow(R.id.donateUsdtTrc20, getString(R.string.donate_usdt_trc20),
                    "TRvVmtB2ztxBa324JxtfaUMB3oAvKKJ1X5", R.drawable.qr_usdt_trc20));
            safeSetup("donateBitcoin", () -> setupDonateRow(R.id.donateBitcoin, getString(R.string.donate_bitcoin),
                    "1NhdHQ14JRx9iV3tshqq7Syj7aMaK38uxg", R.drawable.qr_btc));
            safeSetup("donateEthereum", () -> setupDonateRow(R.id.donateEthereum, getString(R.string.donate_ethereum),
                    "0x4100f28e4c650423eb351fc005c97b8d620d1494", R.drawable.qr_eth));
        } catch (Exception e) {
            LogBuffer.e("About", "onCreate failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void safeSetup(String name, Runnable setup) {
        try {
            setup.run();
        } catch (Exception e) {
            LogBuffer.e("About", "setup " + name + " failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void setupLink(int viewId, String url) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        tv.setOnClickListener(v -> openUrl(url));
    }

    private void setupSpoiler(TextView title, View content) {
        if (title == null || content == null) return;
        title.setOnClickListener(v -> {
            content.setVisibility(content.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDonateRow(int includeId, String label, String address, int qrResId) {
        try {
            View row = findViewById(includeId);
            if (row == null) return;
            TextView tvLabel = row.findViewById(R.id.donateLabel);
            TextView tvAddress = row.findViewById(R.id.donateAddress);
            ImageView ivQr = row.findViewById(R.id.qrCode);
            if (tvLabel != null) tvLabel.setText(label);
            if (tvAddress != null) tvAddress.setText(address);
            if (ivQr != null) {
                if (qrResId != 0) {
                    ivQr.setImageResource(qrResId);
                }
                ivQr.setVisibility(View.GONE);
            }
            if (tvAddress != null) {
                tvAddress.setOnClickListener(v -> {
                    try {
                        copyToClipboard(address);
                        if (qrResId != 0 && ivQr != null) toggleQr(ivQr);
                    } catch (Exception e) {
                        LogBuffer.e("About", "donate row click failed: " + e.getMessage());
                    }
                });
            }
            if (ivQr != null && qrResId != 0) {
                ivQr.setOnClickListener(v -> {
                    try {
                        hideQr(ivQr);
                    } catch (Exception e) {
                        LogBuffer.e("About", "QR click failed: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LogBuffer.e("About", "setupDonateRow failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void setupDonateLink(int includeId, String label, String url, int qrResId) {
        try {
            View row = findViewById(includeId);
            if (row == null) return;

            TextView tvLabel = row.findViewById(R.id.donateLabel);
            TextView tvAddress = row.findViewById(R.id.donateAddress);
            ImageView ivQr = row.findViewById(R.id.qrCode);

            if (tvLabel != null) tvLabel.setText(label);
            if (tvAddress != null) tvAddress.setText(url);
            if (ivQr != null && qrResId != 0) {
                ivQr.setImageResource(qrResId);
            }

            if (tvAddress != null) {
                tvAddress.setOnClickListener(v -> {
                    try {
                        if (ivQr != null && ivQr.getVisibility() == View.VISIBLE) {
                            openUrl(url);
                        } else {
                            showQr(ivQr);
                        }
                    } catch (Exception e) {
                        LogBuffer.e("About", "donate link click failed: " + e.getMessage());
                    }
                });
            }
            if (ivQr != null) {
                ivQr.setOnClickListener(v -> hideQr(ivQr));
            }
        } catch (Exception e) {
            LogBuffer.e("About", "setupDonateLink failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void toggleQr(ImageView ivQr) {
        if (ivQr == null) return;
        if (ivQr.getVisibility() == View.VISIBLE) {
            ivQr.setVisibility(View.GONE);
        } else {
            ivQr.setVisibility(View.VISIBLE);
        }
    }

    private void showQr(ImageView ivQr) {
        if (ivQr == null) return;
        ivQr.setVisibility(View.VISIBLE);
    }

    private void hideQr(ImageView ivQr) {
        if (ivQr == null) return;
        ivQr.setVisibility(View.GONE);
    }

    private void copyToClipboard(String text) {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("donate_address", text));
        }
        Toast.makeText(this, R.string.donate_copied, Toast.LENGTH_SHORT).show();
    }
}
