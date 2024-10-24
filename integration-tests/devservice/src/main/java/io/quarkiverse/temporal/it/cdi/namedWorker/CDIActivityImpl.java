package io.quarkiverse.temporal.it.cdi.namedWorker;

import jakarta.inject.Inject;

import io.quarkiverse.temporal.TemporalActivity;
import io.quarkiverse.temporal.it.cdi.CdiBean;
import io.quarkiverse.temporal.it.cdi.shared.CDIActivity;

@TemporalActivity(workers = "namedWorker")
public class CDIActivityImpl implements CDIActivity {

    @Inject
    CdiBean cdiBean;

    @Override
    public void cdi() {
        cdiBean.someMethod();

    }
}
