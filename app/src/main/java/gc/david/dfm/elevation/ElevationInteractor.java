package gc.david.dfm.elevation;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.List;

import gc.david.dfm.elevation.model.ElevationModel;
import gc.david.dfm.elevation.model.Result;
import gc.david.dfm.executor.Executor;
import gc.david.dfm.executor.Interactor;
import gc.david.dfm.executor.MainThread;

/**
 * Created by david on 05.01.17.
 */
public class ElevationInteractor implements Interactor, ElevationUseCase {

    private final Executor            executor;
    private final MainThread          mainThread;
    private final ElevationRepository repository;

    private Callback     callback;
    private List<LatLng> coordinateList;

    public ElevationInteractor(final Executor executor,
                               final MainThread mainThread,
                               final ElevationRepository repository) {
        this.executor = executor;
        this.mainThread = mainThread;
        this.repository = repository;
    }

    @Override
    public void execute(final List<LatLng> coordinateList, final Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback can't be null, the client of this interactor needs to get the response in the callback");
        }
        this.coordinateList = coordinateList;
        this.callback = callback;
        this.executor.run(this);
    }

    @Override
    public void run() {
        final String coordinatesPath = getCoordinatesPath(coordinateList);

        try {
            final ElevationModel elevationModel = repository.getElevation(coordinatesPath);

            if ("OK".equals(elevationModel.getStatus())) {
                final List<Double> elevationList = getElevationListFromModel(elevationModel);

                mainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onElevationLoaded(elevationList);
                    }
                });
            } else {
                mainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError();
                    }
                });
            }
        } catch (Exception exception) {
            mainThread.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError();
                }
            });
        }
    }

    private String getCoordinatesPath(final List<LatLng> coordinateList) {
        String positionListUrlParameter = "";
        for (int i = 0; i < coordinateList.size(); i++) {
            final LatLng coordinate = coordinateList.get(i);
            positionListUrlParameter += String.valueOf(coordinate.latitude) +
                                        "," +
                                        String.valueOf(coordinate.longitude);
            if (i != coordinateList.size() - 1) {
                positionListUrlParameter += "|";
            }
        }
        return positionListUrlParameter;
    }

    private List<Double> getElevationListFromModel(final ElevationModel elevationModel) {
        return Lists.transform(elevationModel.getResults(), new Function<Result, Double>() {
            @Override
            public Double apply(final Result result) {
                return result.getElevation();
            }
        });
    }
}