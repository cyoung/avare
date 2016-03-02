/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ds.avare;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.LinearLayout;

import com.ds.avare.gps.GpsInterface;
import com.ds.avare.gps.GpsParams;
import com.ds.avare.shapes.ElevationTile;
import com.ds.avare.shapes.Tile;
import com.ds.avare.utils.BitmapHolder;
import com.ds.avare.utils.Helper;

import org.rajawali3d.IRajawaliDisplay;
import org.rajawali3d.Object3D;
import org.rajawali3d.cameras.ChaseCamera;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.NormalMapTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;
import org.rajawali3d.surface.IRajawaliSurface;
import org.rajawali3d.surface.IRajawaliSurfaceRenderer;
import org.rajawali3d.terrain.SquareTerrain;
import org.rajawali3d.terrain.TerrainGenerator;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author zkhan
 * Main activity
 */
public class ThreeDActivity extends Activity implements IRajawaliDisplay {

    private StorageService mService;
    protected IRajawaliSurface mRajawaliSurface;
    protected IRajawaliSurfaceRenderer mRenderer;
    protected LinearLayout mLayout;
    private Object3D mAirplane;
    private ChaseCamera mChaseCamera;
    private SquareTerrain mTerrain;
    private String mLastTileName;

    /*
     * Start GPS
     */
    private GpsInterface mGpsInfc = new GpsInterface() {

        @Override
        public void statusCallback(GpsStatus gpsStatus) {
        }

        @Override
        public void locationCallback(Location location) {
            GpsParams params = new GpsParams(location);
            double track = params.getBearing();
            double lat = params.getLatitude();
            double lon = params.getLongitude();
            double height = params.getAltitude();

            if(mService == null) {
                return;
            }
            // Get the elevation tile for terrain
            ElevationTile etile = mService.getElevationTile();
            if(etile == null) {
                return;
            }
            Tile tile = etile.getTile();

            if(tile == null) {
                return;
            }
            // Find our position on tile
            double offsx = tile.getOffsetX(lon);
            double offsy = tile.getOffsetY(lat);

            // New terrain, set it
            if(!tile.getName().equals(mLastTileName)) {
                BitmapHolder bmp = etile.getElevationBitmap();
                if(bmp.getBitmap() != null) {
                    mLastTileName = tile.getName();
                    ((TerrainRenderer) mRenderer).addTerrain(bmp.getBitmap());
                }
            }

            // find height from GPS and put in y, 50 for test
            mAirplane.setPosition(offsx, 50, offsy);
            mChaseCamera.setCameraYaw(track);


        }

        @Override
        public void timeoutCallback(boolean timeout) {
        }

        @Override
        public void enabledCallback(boolean enabled) {
        }
    };

    /*
     * For being on tab this activity discards back to main activity
     * (non-Javadoc)
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void onBackPressed() {
        ((MainActivity)this.getParent()).showMapTab();
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Helper.setTheme(this);
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mService = null;
        mLastTileName = "";


        LayoutInflater layoutInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mLayout = (LinearLayout) layoutInflater.inflate(getLayoutID(), null);

        mRajawaliSurface = (IRajawaliSurface)mLayout.findViewById(R.id.rajwali_surface);
        mRenderer = createRenderer();
        applyRenderer();

        setContentView(mLayout);
    }

    @Override
    public int getLayoutID() {
        return R.layout.threed_layout;
    }

    protected void applyRenderer() {
        mRajawaliSurface.setSurfaceRenderer(mRenderer);
    }


    /** Defines callbacks for service binding, passed to bindService() */
    /**
     *
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        /* (non-Javadoc)
         * @see android.content.ServiceConnection#onServiceConnected(android.content.ComponentName, android.os.IBinder)
         */
        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            /*
             * We've bound to LocalService, cast the IBinder and get LocalService instance
             */
            StorageService.LocalBinder binder = (StorageService.LocalBinder)service;
            mService = binder.getService();
            mService.registerGpsListener(mGpsInfc);
        }

        /* (non-Javadoc)
         * @see android.content.ServiceConnection#onServiceDisconnected(android.content.ComponentName)
         */
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };
    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    public void onResume() {
        super.onResume();

        Helper.setOrientationAndOn(this);

        /*
         * Registering our receiver
         * Bind now.
         */
        Intent intent = new Intent(this, StorageService.class);
        getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();
        getApplicationContext().unbindService(mConnection);
        
        if(null != mService) {
            mService.unregisterGpsListener(mGpsInfc);
        }
    }

    @Override
    public TerrainRenderer createRenderer() {
        // Create the renderer
        mLastTileName = "";
        return new TerrainRenderer(getApplicationContext());
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mRenderer.onTouchEvent(event);
        return true;
    }


    /**
     *
     */
    private final class TerrainRenderer extends RajawaliRenderer {


        public TerrainRenderer(Context context) {
            super(context);
        }

        @Override  //This method moves the camera using the Android home screen swipe output. It's a better way, but not always supported
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep, int xPixels, int yPixels) {
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
        }


        @Override
        public void onRenderSurfaceCreated(EGLConfig config, GL10 gl, int width, int height) {
            super.onRenderSurfaceCreated(config, gl, width, height);
        }

        @Override
        protected void onRender(long ellapsedRealtime, double deltaTime) {
            super.onRender(ellapsedRealtime, deltaTime);
        }

        public void addTerrain(Bitmap bmp) {

            if(mTerrain != null) {
                getCurrentScene().removeChild(mTerrain);
            }
            //
            // -- Load a bitmap that represents the terrainbw. Its color values will
            //    be used to generate heights.
            //

            //
            // -- A normal map material will give the terrainbw a bit more detail.
            //
            Material material = new Material();
            material.enableLighting(true);
            material.useVertexColors(true);
            material.setDiffuseMethod(new DiffuseMethod.Lambert());
            try {
                Texture groundTexture = new Texture("ground", R.drawable.ground);
                groundTexture.setInfluence(.5f);
                material.addTexture(groundTexture);
                material.addTexture(new NormalMapTexture("groundNormalMap", R.drawable.groundnor));
                material.setColorInfluence(0);
            }
            catch (ATexture.TextureException e) {
            }

            try {
                SquareTerrain.Parameters terrainParams = SquareTerrain.createParameters(bmp);
                // -- set terrain scale, this needs to be found from tile projection
                terrainParams.setScale(4f, 160f, 4f);
                // -- the number of plane subdivisions
                terrainParams.setDivisions(512);
                // -- the number of times the textures should be repeated
                terrainParams.setTextureMult(4);
                //
                // -- Terrain colors can be set by manually specifying base, middle and
                //    top colors.
                //
                terrainParams.setBasecolor(Color.argb(255, 0, 0, 0));
                terrainParams.setMiddleColor(Color.argb(255, 200, 200, 200));
                terrainParams.setUpColor(Color.argb(255, 0, 30, 0));
                //
                mTerrain = TerrainGenerator.createSquareTerrainFromBitmap(terrainParams, true);
            }
            catch (Exception e) {
            }

            //
            // -- Blend the texture with the vertex colors
            //
            material.setColorInfluence(.5f);

            mTerrain.setMaterial(material);

            getCurrentScene().addChild(mTerrain);

        }

        @Override
        public void initScene() {
            getCurrentScene().setBackgroundColor(0x999999);

            //
            // -- Use a chase camera that follows and invisible object
            //    and add fog for a nice effect.
            //

            mChaseCamera = new ChaseCamera(new Vector3(0, 0, 0));
            mChaseCamera.setFarPlane(1024);
            getCurrentScene().replaceAndSwitchCamera(mChaseCamera, 0);


            DirectionalLight light = new DirectionalLight(0f, 10f, 0f);
            light.setPower(1f);
            getCurrentScene().addLight(light);

            //
            // -- The empty object that will move along a curve and that
            //    will be followed by the camera
            //
            mAirplane = new Sphere(1, 2, 2);
            mAirplane.setVisible(false);
            mAirplane.setPosition(0, 0, 0);

            //
            // -- Tell the camera to chase the empty.
            //
            mChaseCamera.setLinkedObject(mAirplane);

        }

    }

}
