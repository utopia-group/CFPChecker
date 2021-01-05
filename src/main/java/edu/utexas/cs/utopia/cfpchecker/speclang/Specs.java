package edu.utexas.cs.utopia.cfpchecker.speclang;

import soot.*;

import java.util.*;

/**
 * Created by kferles on 6/28/18.
 */
public class Specs
{
    static public Specification createChainedSpec() {
        Scene sootScene = Scene.v();

        // Create the spec
        SootClass mainClass = sootScene.getMainClass();

        Set<SootClass> specApiClasses = Collections.singleton(mainClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall fooCall = new APISpecCall(mainClass.getMethodByName("nd$foo"), lockReceiver);
        APISpecCall barCall = new APISpecCall(mainClass.getMethodByName("nd$bar"), lockReceiver);

        NonTerminal startSymbol = new NonTerminal("S");
        NonTerminal A = new NonTerminal("A");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                new SpecRule(startSymbol, A, startSymbol),
                new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION),
                new SpecRule(A, fooCall, A, barCall),
                new SpecRule(A, Terminal.EPSILON_TRANSITION)));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createNestedSpec() {
        Scene sootScene = Scene.v();

        // Create the spec
        SootClass mainClass = sootScene.getMainClass();

        Set<SootClass> specApiClasses = Collections.singleton(mainClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall fooCall = new APISpecCall(mainClass.getMethodByName("nd$foo"), lockReceiver);
        APISpecCall barCall = new APISpecCall(mainClass.getMethodByName("nd$bar"), lockReceiver);

        NonTerminal startSymbol = new NonTerminal("S");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                new SpecRule(startSymbol, fooCall, startSymbol, barCall, startSymbol),
                new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));

        return new Specification(rules, specApiClasses);
    }

    static public Specification createUnbalancedSpec() {
        Scene sootScene = Scene.v();

        // Create the spec
        SootClass mainClass = sootScene.getMainClass();

        Set<SootClass> specApiClasses = Collections.singleton(mainClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall fooCall = new APISpecCall(mainClass.getMethodByName("nd$foo"), lockReceiver);
        APISpecCall barCall = new APISpecCall(mainClass.getMethodByName("nd$bar"), lockReceiver);

        NonTerminal startSymbol = new NonTerminal("S");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                new SpecRule(startSymbol, fooCall, startSymbol, barCall),
                new SpecRule(startSymbol, startSymbol, barCall),
                new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));

        return new Specification(rules, specApiClasses);
    }

    static public Specification createWrappedSpec() {
        Scene sootScene = Scene.v();

        // Create the spec
        SootClass mainClass = sootScene.getMainClass();

        Set<SootClass> specApiClasses = Collections.singleton(mainClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall fooCall = new APISpecCall(mainClass.getMethodByName("nd$foo"), lockReceiver);
        APISpecCall barCall = new APISpecCall(mainClass.getMethodByName("nd$bar"), lockReceiver);

        NonTerminal startSymbol = new NonTerminal("S");
        NonTerminal S1 = new NonTerminal("S1");
        NonTerminal A = new NonTerminal("A");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                new SpecRule(startSymbol, S1),
                new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION),
                new SpecRule(S1, A),
                new SpecRule(S1, fooCall, S1, fooCall),
                new SpecRule(A, barCall),
                new SpecRule(A, A, barCall)));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createReentrantLockSpec()
    {
        Scene sootScene = Scene.v();

        // Useful class names.
        String reentrantLockClassName = "java.util.concurrent.locks.ReentrantLock";

        // Create the spec
        SootClass reentrantLockClass = sootScene.getSootClass(reentrantLockClassName);

        Set<SootClass> specApiClasses = Collections.singleton(reentrantLockClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall lockCall = new APISpecCall(reentrantLockClass.getMethodByName("lock"), lockReceiver);
        APISpecCall unlockCall = new APISpecCall(reentrantLockClass.getMethodByName("unlock"), lockReceiver);

        NonTerminal startSymbol = new NonTerminal("S");
        NonTerminal s1 = new NonTerminal("S1");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(new SpecRule(startSymbol, s1),
                                                             new SpecRule(s1, lockCall, s1, unlockCall),
                                                             new SpecRule(s1, Terminal.EPSILON_TRANSITION)));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createWakeLockSpec()
    {
        Scene sootScene = Scene.v();

        // Useful class names.
        String reentrantLockClassName = "android.os.PowerManager$WakeLock";

        // Create the spec
        SootClass reentrantLockClass = sootScene.getSootClass(reentrantLockClassName);

        Set<SootClass> specApiClasses = Collections.singleton(reentrantLockClass);
        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall lockCall = new APISpecCall(reentrantLockClass.getMethod("acquire", new ArrayList()), lockReceiver);
        APISpecCall unlockCall = new APISpecCall(reentrantLockClass.getMethod("release", new ArrayList()), lockReceiver);
        APISpecCall refCntCall = new APISpecCall(reentrantLockClass.getMethod("setReferenceCounted", Collections.singletonList(BooleanType.v())), new Wildcard[]{lockReceiver[0], Wildcard.DONT_CARE_VALUE});

        NonTerminal startSymbol = new NonTerminal("S");
        NonTerminal refCnt = new NonTerminal("RefCnt");
        NonTerminal nonRefCnt = new NonTerminal("NonRefCnt");
        NonTerminal nonRefAcq = new NonTerminal("NonRefAcq");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                // S -> RefCnt
                new SpecRule(startSymbol, refCnt),
                // S -> setRefCnt NonRefCnt
                new SpecRule(startSymbol, refCntCall, nonRefCnt),
                // NonRefCnt -> ε
                new SpecRule(nonRefCnt, Terminal.EPSILON_TRANSITION),
                // NonRefCnt -> NonRefAcq release
                new SpecRule(nonRefCnt, nonRefAcq, unlockCall),
                // NonRefAcq -> acquire NonRefAcq release NonRefAcq
                new SpecRule(nonRefAcq, lockCall, nonRefAcq, unlockCall, nonRefAcq),
                // NonRefAcq -> acquire NonRefAcq
                new SpecRule(nonRefAcq, lockCall, nonRefAcq),
                // NonRefAcq -> acquire
                new SpecRule(nonRefAcq, lockCall),
                // RefCnt -> ε
                new SpecRule(refCnt, Terminal.EPSILON_TRANSITION),
                // RefCnt -> acquire RefCnt release RefCnt
                new SpecRule(refCnt, lockCall, refCnt, unlockCall, refCnt)
        ));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createWifiLockSpec()
    {
        Scene sootScene = Scene.v();

        // Useful class names.
        String reentrantLockClassName = "android.net.wifi.WifiManager$WifiLock";

        // Create the spec
        SootClass reentrantLockClass = sootScene.getSootClass(reentrantLockClassName);

        Set<SootClass> specApiClasses = Collections.singleton(reentrantLockClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};


        APISpecCall lockCall = new APISpecCall(reentrantLockClass.getMethod("acquire", new ArrayList()), lockReceiver);
        APISpecCall unlockCall = new APISpecCall(reentrantLockClass.getMethod("release", new ArrayList()), lockReceiver);
        APISpecCall refCntCall = new APISpecCall(reentrantLockClass.getMethod("setReferenceCounted", Collections.singletonList(BooleanType.v())), new Wildcard[]{lockReceiver[0], Wildcard.DONT_CARE_VALUE});

        NonTerminal startSymbol = new NonTerminal("S");
        NonTerminal refCnt = new NonTerminal("RefCnt");
        NonTerminal nonRefCnt = new NonTerminal("NonRefCnt");
        NonTerminal nonRefAcq = new NonTerminal("NonRefAcq");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                // S -> RefCnt
                new SpecRule(startSymbol, refCnt),
                // S -> setRefCnt NonRefCnt
                new SpecRule(startSymbol, refCntCall, nonRefCnt),
                // NonRefCnt -> ε
                new SpecRule(nonRefCnt, Terminal.EPSILON_TRANSITION),
                // NonRefCnt -> NonRefAcq release
                new SpecRule(nonRefCnt, nonRefAcq, unlockCall),
                // NonRefAcq -> acquire NonRefAcq release NonRefAcq
                new SpecRule(nonRefAcq, lockCall, nonRefAcq, unlockCall, nonRefAcq),
                // NonRefAcq -> acquire NonRefAcq
                new SpecRule(nonRefAcq, lockCall, nonRefAcq),
                // NonRefAcq -> acquire
                new SpecRule(nonRefAcq, lockCall),
                // RefCnt -> ε
                new SpecRule(refCnt, Terminal.EPSILON_TRANSITION),
                // RefCnt -> acquire RefCnt release RefCnt
                new SpecRule(refCnt, lockCall, refCnt, unlockCall, refCnt)
        ));

        return new Specification(rules, specApiClasses);
    }

    static public Specification createChainedReentrantLockSpec()
    {
        Scene sootScene = Scene.v();

        // Useful class names.
        String reentrantLockClassName = "java.util.concurrent.locks.ReentrantLock";

        // Create the spec
        SootClass reentrantLockClass = sootScene.getSootClass(reentrantLockClassName);

        Set<SootClass> specApiClasses = Collections.singleton(reentrantLockClass);

        Wildcard[] lockReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall lockCall = new APISpecCall(reentrantLockClass.getMethodByName("lock"), lockReceiver);
        APISpecCall unlockCall = new APISpecCall(reentrantLockClass.getMethodByName("unlock"), lockReceiver);

        NonTerminal startSymbol = new NonTerminal("S");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(new SpecRule(startSymbol, lockCall, startSymbol, unlockCall, startSymbol),
                                                             new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createCanvasSaveRestoreSpec()
    {
        Scene sootScene = Scene.v();

        String canvasClassName = "android.graphics.Canvas";

        SootClass canvasClass = sootScene.getSootClass(canvasClassName);

        Set<SootClass> specApiClasses = Collections.singleton(canvasClass);

        Wildcard[] canvasReceiver = new Wildcard[]{new Wildcard(1)};

        APISpecCall saveCall = new APISpecCall(canvasClass.getMethod("save", Collections.emptyList()), canvasReceiver);
        APISpecCall restoreCall = new APISpecCall(canvasClass.getMethod("restore", Collections.emptyList()), canvasReceiver);
        APISpecCall restoreToCountCall = new APISpecCall(canvasClass.getMethod("restoreToCount", Collections.singletonList(IntType.v())), new Wildcard[]{canvasReceiver[0], Wildcard.DONT_CARE_VALUE});

        NonTerminal startSymbol = new NonTerminal("S");
        NonTerminal restoreSymbol = new NonTerminal("R");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(// S -> save S restore S
                                                             new SpecRule(startSymbol, saveCall, startSymbol, restoreSymbol, startSymbol),
                                                             // S -> S restore
                                                             new SpecRule(startSymbol, saveCall, startSymbol),
                                                             // S -> ε
                                                             new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION),
                                                            // R -> restore | restoreToCount
                                                            new SpecRule(restoreSymbol, restoreCall),
                                                            new SpecRule(restoreSymbol, restoreToCountCall)));

        return new Specification(rules, specApiClasses);
    }

    static public Specification createSensorManagerListenerSpec()
    {
        Scene sootScene = Scene.v();

        String sensorManagerClassName = "android.hardware.SensorManager";
        String sensorEventListenerClassName = "android.hardware.SensorEventListener";
        String sensorClassName = "android.hardware.Sensor";

        SootClass sensorManagerClass = sootScene.getSootClass(sensorManagerClassName);
        SootClass sensorEventListenerClass = sootScene.getSootClass(sensorEventListenerClassName);
        SootClass sensorClass = sootScene.getSootClass(sensorClassName);

        Set<SootClass> specApiClasses = Collections.singleton(sensorManagerClass);

        Wildcard sensorManagerReceiver = new Wildcard(1);
        Wildcard listenerArg = new Wildcard(2);

        APISpecCall registerCall = new APISpecCall(sensorManagerClass.getMethod("registerListener", Arrays.asList(sensorEventListenerClass.getType(),
                                                                                                                    sensorClass.getType(),
                                                                                                                    IntType.v()),
                                                                                BooleanType.v()),
                                                   new Wildcard[]{sensorManagerReceiver, listenerArg, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE});
        APISpecCall unregisterCall = new APISpecCall(sensorManagerClass.getMethod("unregisterListener", Collections.singletonList(sensorEventListenerClass.getType())),
                                                     new Wildcard[]{sensorManagerReceiver, listenerArg});

        NonTerminal startSymbol = new NonTerminal("S");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(// S -> registerListener S unregisterListener S
                                                             new SpecRule(startSymbol, registerCall, startSymbol, unregisterCall, startSymbol),
                                                             // S -> ε
                                                             new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createLocationManagerLooperSpec() {
        Scene sootScene = Scene.v();

        String locationManagerClassName = "android.location.LocationManager";
        String locationListenerClassName = "android.location.LocationListener";
        //String sensorClassName = "android.hardware.Sensor";
        String looperClassName = "android.os.Looper";

        SootClass locationManagerClass = sootScene.getSootClass(locationManagerClassName);
        SootClass locationListenerClass = sootScene.getSootClass(locationListenerClassName);
        SootClass stringClass = sootScene.getSootClass("java.lang.String");
        SootClass looperClass = sootScene.getSootClass(looperClassName);
        //SootClass sensorClass = sootScene.getSootClass(sensorClassName);

        Set<SootClass> specApiClasses = Collections.singleton(locationManagerClass);

        Wildcard locationManagerReceiver = new Wildcard(1);
        Wildcard listenerArg = new Wildcard(2);

        APISpecCall registerCall = new APISpecCall(locationManagerClass.getMethod("requestLocationUpdates", Arrays.asList(stringClass.getType(), LongType.v(), FloatType.v(), locationListenerClass.getType(), looperClass.getType())),
                new Wildcard[]{locationManagerReceiver, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE, listenerArg, Wildcard.DONT_CARE_VALUE});
                //new Wildcard[]{sensorManagerReceiver, listenerArg, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE});
        APISpecCall unregisterCall = new APISpecCall(locationManagerClass.getMethod("removeUpdates", Collections.singletonList(locationListenerClass.getType())),
                new Wildcard[]{locationManagerReceiver, listenerArg});

        NonTerminal startSymbol = new NonTerminal("S");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(// S -> registerListener S unregisterListener S
                new SpecRule(startSymbol, registerCall, startSymbol, unregisterCall, startSymbol),
                // S -> ε
                new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));
        return new Specification(rules, specApiClasses);
    }

    static public Specification createLocationManagerSpec() {
        Scene sootScene = Scene.v();

        String locationManagerClassName = "android.location.LocationManager";
        String locationListenerClassName = "android.location.LocationListener";

        SootClass locationManagerClass = sootScene.getSootClass(locationManagerClassName);
        SootClass locationListenerClass = sootScene.getSootClass(locationListenerClassName);
        SootClass stringClass = sootScene.getSootClass("java.lang.String");

        Set<SootClass> specApiClasses = Collections.singleton(locationManagerClass);

        Wildcard locationManagerReceiver = new Wildcard(1);
        Wildcard listenerArg = new Wildcard(2);
        listenerArg.setDistinct(true);

        APISpecCall registerCall = new APISpecCall(locationManagerClass.getMethod("requestLocationUpdates", Arrays.asList(stringClass.getType(), LongType.v(), FloatType.v(), locationListenerClass.getType())),
                new Wildcard[]{locationManagerReceiver, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE, listenerArg});

        //new Wildcard[]{sensorManagerReceiver, listenerArg, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE});
        APISpecCall unregisterCall = new APISpecCall(locationManagerClass.getMethod("removeUpdates", Collections.singletonList(locationListenerClass.getType())),
                new Wildcard[]{locationManagerReceiver, listenerArg});

        NonTerminal startSymbol = new NonTerminal("S");

        List<SpecRule> rules;

        if(locationManagerClass.declaresMethod("requestLocationUpdates", Arrays.asList(IntType.v(), LongType.v(), IntType.v(), locationListenerClass.getType()))) {
            APISpecCall altRegisterCall = new APISpecCall(locationManagerClass.getMethod("requestLocationUpdates", Arrays.asList(IntType.v(), LongType.v(), IntType.v(), locationListenerClass.getType())),
                    new Wildcard[]{locationManagerReceiver, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE, listenerArg});

            rules = new ArrayList<>(Arrays.asList(// S -> registerListener S unregisterListener S
                    new SpecRule(startSymbol, altRegisterCall, startSymbol, unregisterCall, startSymbol),
                    // S -> ε
                    new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));
        }
        else {
            rules = new ArrayList<>(Arrays.asList(// S -> registerListener S unregisterListener S
                    new SpecRule(startSymbol, registerCall, startSymbol, unregisterCall, startSymbol),
                    // S -> ε
                    new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION)));
        }


        return new Specification(rules, specApiClasses);
    }

    static public Specification createJsonGeneratorSpec()
    {
        Scene sootScence = Scene.v();

        String jsonGenClassName = "com.fasterxml.jackson.core.JsonGenerator";

        SootClass jsonGenClass = sootScence.getSootClass(jsonGenClassName);

        Wildcard jsonGenObj = new Wildcard(1);

        SootClass stringClass = sootScence.getSootClass("java.lang.String");

        APISpecCall writeStringCall = new APISpecCall(jsonGenClass.getMethod("writeString", Collections.singletonList(stringClass.getType())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE}),
                    writeStringFieldCall = new APISpecCall(jsonGenClass.getMethod("writeStringField", Arrays.asList(stringClass.getType(), stringClass.getType())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE}),
                    writeNumberCall = new APISpecCall(jsonGenClass.getMethod("writeNumber", Collections.singletonList(IntType.v())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE}),
                    writeNumberFieldCall = new APISpecCall(jsonGenClass.getMethod("writeNumberField", Arrays.asList(stringClass.getType(), IntType.v())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE}),
                    writeBooleanCall = new APISpecCall(jsonGenClass.getMethod("writeBoolean", Collections.singletonList(BooleanType.v())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE}),
                    writeBooleanFieldCall = new APISpecCall(jsonGenClass.getMethod("writeBooleanField", Arrays.asList(stringClass.getType(), BooleanType.v())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE, Wildcard.DONT_CARE_VALUE}),
                    writeFieldNameCall = new APISpecCall(jsonGenClass.getMethod("writeFieldName", Collections.singletonList(stringClass.getType())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE}),
                    writeStartObjectCall = new APISpecCall(jsonGenClass.getMethod("writeStartObject", Collections.emptyList()), new Wildcard[] {jsonGenObj}),
                    writeEndObjectCall = new APISpecCall(jsonGenClass.getMethod("writeEndObject", Collections.emptyList()), new Wildcard[] {jsonGenObj}),
                    writeArrayFieldStartCall = new APISpecCall(jsonGenClass.getMethod("writeArrayFieldStart", Collections.singletonList(stringClass.getType())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE}),
                    writeStartArrayCall = new APISpecCall(jsonGenClass.getMethod("writeStartArray", Collections.emptyList()), new Wildcard[] {jsonGenObj}),
                    writeObjectFieldStartCall = new APISpecCall(jsonGenClass.getMethod("writeObjectFieldStart", Collections.singletonList(stringClass.getType())), new Wildcard[]{jsonGenObj, Wildcard.DONT_CARE_VALUE}),
                    writeEndArrayCall = new APISpecCall(jsonGenClass.getMethod("writeEndArray", Collections.emptyList()), new Wildcard[] {jsonGenObj});

        NonTerminal startSymbol = new NonTerminal("S"),
                    objSymbol = new NonTerminal("Obj"),
                    fieldSymbol = new NonTerminal("Field"),
                    arraySymbol = new NonTerminal("Array"),
                    valListSymbol = new NonTerminal("Vals");

        List<SpecRule> rules = new ArrayList<>(Arrays.asList(
                new SpecRule(startSymbol, objSymbol),
                new SpecRule(startSymbol, arraySymbol),
                new SpecRule(startSymbol, writeStringCall),
                new SpecRule(startSymbol, writeNumberCall),
                new SpecRule(startSymbol, writeBooleanCall),
                new SpecRule(startSymbol, Terminal.EPSILON_TRANSITION),

                // Objects
                new SpecRule(objSymbol, writeStartObjectCall, fieldSymbol, writeEndObjectCall),

                // Fields
                new SpecRule(fieldSymbol, writeFieldNameCall, startSymbol, fieldSymbol),
                new SpecRule(fieldSymbol, writeStringFieldCall, fieldSymbol),
                new SpecRule(fieldSymbol, writeNumberFieldCall, fieldSymbol),
                new SpecRule(fieldSymbol, writeBooleanFieldCall, fieldSymbol),
                new SpecRule(fieldSymbol, writeArrayFieldStartCall, valListSymbol, writeEndArrayCall, fieldSymbol),
                new SpecRule(fieldSymbol, writeObjectFieldStartCall, fieldSymbol, writeEndObjectCall, fieldSymbol),
                new SpecRule(fieldSymbol, Terminal.EPSILON_TRANSITION),

                // Arrays
                new SpecRule(arraySymbol, writeStartArrayCall, valListSymbol, writeEndArrayCall),

                // Value list
                new SpecRule(valListSymbol, startSymbol, valListSymbol),
                new SpecRule(valListSymbol, Terminal.EPSILON_TRANSITION)
        ));

        return new Specification(rules, Collections.singleton(jsonGenClass));
    }
}
