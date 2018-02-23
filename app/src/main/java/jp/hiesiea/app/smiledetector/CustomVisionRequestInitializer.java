package jp.hiesiea.app.smiledetector;

import android.content.pm.PackageManager;

import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;

import java.io.IOException;

import jp.hiesiea.app.smiledetector.utils.PackageManagerUtils;

public class CustomVisionRequestInitializer extends VisionRequestInitializer {
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";

    private String mPackageName;
    private PackageManager mPackageManager;

    public CustomVisionRequestInitializer(String key, String packageName, PackageManager packageManager) {
        super(key);
        mPackageName = packageName;
        mPackageManager = packageManager;
    }

    /**
     * We override this so we can inject important identifying fields into the HTTP
     * headers. This enables use of a restricted cloud platform API key.
     */
    @Override
    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
            throws IOException {
        super.initializeVisionRequest(visionRequest);
        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, mPackageName);
        String sig = PackageManagerUtils.getSignature(mPackageManager, mPackageName);
        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
    }
}
