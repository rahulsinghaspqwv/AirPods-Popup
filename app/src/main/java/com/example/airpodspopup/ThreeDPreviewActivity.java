package com.example.airpodspopup;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.Filament;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.View;
import com.google.android.filament.gltfio.AssetLoader;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.gltfio.MaterialProvider;
import com.google.android.filament.gltfio.ResourceLoader;
import com.google.android.filament.gltfio.UbershaderProvider;
import com.google.android.filament.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;



public class ThreeDPreviewActivity extends AppCompatActivity implements SurfaceHolder.Callback{
    private com.google.android.filament.Skybox skybox;

    private SurfaceView surfaceView;

    private Engine engine;
    private Renderer renderer;
    private Scene scene;
    private View view;
    private Camera camera;
    private SwapChain swapChain;

    private AssetLoader assetLoader;
    private ResourceLoader resourceLoader;
    private MaterialProvider materialProvider;
    private FilamentAsset asset;

    private boolean rendering = false;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!rendering) return;
            Choreographer.getInstance().postFrameCallback(this);
            if (renderer == null || swapChain == null || view == null) {
                return;
            }
            if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                renderer.render(view);
                renderer.endFrame();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_three_dpreview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Filament.init();
        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);
        engine = Engine.create();
        renderer = engine.createRenderer();
        scene = engine.createScene();
        view = engine.createView();
        camera = engine.createCamera(com.google.android.filament.EntityManager.get().create());
        view.setCamera(camera);
        view.setScene(scene);
//        view.setClearOptions(new View.ClearOptions().clearColor(0.08f, 0.08f, 0.10f, 1.0f).clear(true));

        com.google.android.filament.Skybox skybox = new com.google.android.filament.Skybox.Builder().color(0.08f, 0.08f, 0.10f, 1.0f).build(engine);
        scene.setSkybox(skybox);

        camera.lookAt(0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        surfaceView.getHolder().addCallback(this);
        try {
            loadGlb();
        } catch (Exception e) {
            Log.e("AirPods3D", "GLB load failed", e);
            Toast.makeText(this, "3D model loading failed", Toast.LENGTH_LONG).show();
        }
    }

    private void loadGlb() throws Exception {
        materialProvider = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materialProvider, com.google.android.filament.EntityManager.get());
        resourceLoader = new ResourceLoader(engine);
        ByteBuffer buffer = readAsset("models/airpods_pro_3d.glb");
        asset = assetLoader.createAsset(buffer);
        if (asset == null) {
            throw new RuntimeException("Invalid GLB model");
        }
        resourceLoader.loadResources(asset);
        scene.addEntities(asset.getEntities());
        Log.d("AirPods3D", "GLB loaded successfully");
    }

    private ByteBuffer readAsset(String path) throws Exception {
        InputStream input = getAssets().open(path);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int count;
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
        }
        input.close();
        byte[] bytes = output.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        buffer.put(bytes);
        buffer.flip();

        return buffer;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        if (surface != null && surface.isValid()) {
            swapChain = engine.createSwapChain(surface);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (view != null) {
            view.setViewport(new com.google.android.filament.Viewport(0, 0, width, height));
        }

        if (camera != null && height > 0) {
            camera.setProjection(45.0, (double) width / height, 0.1, 100.0, Camera.Fov.VERTICAL);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (swapChain != null) {
            engine.destroySwapChain(swapChain);
            swapChain = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        rendering = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onPause() {
        rendering = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        rendering = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        if (engine != null) {
            if (swapChain != null) {
                engine.destroySwapChain(swapChain);
            }
            if (asset != null) {
                scene.removeEntities(asset.getEntities());
                assetLoader.destroyAsset(asset);
            }
            if (resourceLoader != null) {
                resourceLoader.destroy();
            }
            if (assetLoader != null) {
                assetLoader.destroy();
            }
            if (materialProvider != null) {
                materialProvider.destroyMaterials();
                materialProvider.destroy();
            }
            if (view != null) engine.destroyView(view);
            scene.setSkybox(null);
            engine.destroySkybox(skybox);
            if (scene != null) engine.destroyScene(scene);
            if (renderer != null) engine.destroyRenderer(renderer);
            if (camera != null) engine.destroyCameraComponent(camera.getEntity());
            engine.destroy();
        }
        super.onDestroy();
    }
}