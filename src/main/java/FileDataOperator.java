import org.jfree.data.time.Millisecond;
import org.json.JSONObject;

import java.util.Date;

/**
 * Created by vitaly on 11.06.16.
 */
public class FileDataOperator {

    private double filterT = 1.0;
    private double tettaLast = 0.0;
    private double gammaLast = 0.0;
    private double psyLast = 0.0;
    private double gainFilter = 1.0;

    Main main;
    private double[][] matrixD;
    private double[][] D1;
    private final int constG = 16384;
    private final int constW = 131;
    private final double calibrKoef = 20;
    private final double deltaT = 0.0064; //seconds
    int num = 0;
    double S;
    double psy = 0.0;

    int gx0 = -332;
    int gy0 = -91;
    int gz0 = 184;

    int calibrationCounter = 0;

    long start;
    boolean isCalibrated = false;

    int i = 0;
    Millisecond time;

    double lastAX = 0.0;
    double lastAY = 0.0;
    double lastAZ = 0.0;

    double lastWX = 0.0;
    double lastWY = 0.0;
    double lastWZ = 0.0;

    long gxSum = 0;
    long gySum = 0;
    long gzSum = 0;

    long axSum = 0;
    long aySum = 0;
    long azSum = 0;

    public FileDataOperator(Main main) {
        matrixD = new double[4][4];
        matrixD[1][1] = 1.0;
        matrixD[2][2] = 1.0;
        matrixD[3][3] = 1.0;

        D1 = new double[4][4];
        D1[1][1] = 1.0;
        D1[2][2] = 1.0;
        D1[3][3] = 1.0;

        this.main = main;
        start = System.currentTimeMillis();
        time = new Millisecond();
    }

    public void addData(JSONObject jsonObject){
        i++;


        num++;

        double wXR = jsonObject.getInt("gx");
        double wYR = jsonObject.getInt("gy");
        double wZR = jsonObject.getInt("gz");

        wXR = filter(wXR, lastWX, filterT);
        wYR = filter(wYR, lastWY, filterT);
        wZR = filter(wZR, lastWZ, filterT);

        lastWX = wXR;
        lastWY = wYR;
        lastWZ = wZR;

        //calibrate
        double wx = (0.9684 * ( wXR - gx0) + 0.0036 * (wYR - gy0) + 0.0299 * (wZR - gz0)) / constW;
        double wy = (0.9965 * ( wYR - gy0) + 0.0110 * (wXR - gx0) + 0.0046 * (wZR - gz0)) / constW;
        double wz = (0.9854 * ( wZR - gz0) - 0.0515 * (wXR - gx0) - 0.0304 * (wZR - gz0)) / constW;

        double aXR = jsonObject.getInt("ax");
        double aYR = jsonObject.getInt("ay");
        double aZR = jsonObject.getInt("az");

        aXR = filter(aXR, lastAX, filterT);
        aYR = filter(aYR, lastAY, filterT);
        aZR = filter(aZR, lastAZ, filterT);

        lastAX = aXR;
        lastAY = aYR;
        lastAZ = aZR;

        double ax = (0.9939 * ( aXR - 547.34) - 0.0113 * (aYR - 4.67  ) + 0.0346 * (aZR + 661.95) ) / constG * 9.81; // m/c^2  //547
        double ay = (0.9977 * ( aYR - 4.67  ) + 0.0110 * (aXR - 547.34) - 0.0056 * (aZR + 661.95) ) / constG * 9.81; // m/c^2
        double az = (0.9854 * ( aZR + 661.95) - 0.0324 * (aXR - 547.34) - 0.0040 * (aYR - 4.67  ) ) / constG * 9.81; // m/c^2
        //calibrated

        //body to local level

        double fE = matrixD[1][1] * ax + matrixD[1][2] * ay + matrixD[1][3] * az;
        double fN = matrixD[2][1] * ax + matrixD[2][2] * ay + matrixD[2][3] * az;
        //double fUp = matrixD[3][1] * ax + matrixD[3][2] * ay + matrixD[3][3] * az;

        //Puasson
        double wEC = -calibrKoef * fN; //-
        double wNC = calibrKoef * fE;
        double wUpC = -psy / deltaT / 500.0;

        D1[1][1] = matrixD[1][1] + deltaT * (matrixD[1][2] * wz - matrixD[1][3] * wy + matrixD[2][1] * wUpC - matrixD[3][1] * wNC);
        D1[1][2] = matrixD[1][2] + deltaT * (matrixD[1][3] * wx - matrixD[1][1] * wz + matrixD[2][2] * wUpC - matrixD[3][2] * wNC);
        D1[1][3] = matrixD[1][3] + deltaT * (matrixD[1][1] * wy - matrixD[1][2] * wx + matrixD[2][3] * wUpC - matrixD[3][3] * wNC);

        D1[2][1] = matrixD[2][1] + deltaT * (matrixD[2][2] * wz - matrixD[2][3] * wy - matrixD[1][1] * wUpC + matrixD[3][1] * wEC);
        D1[2][2] = matrixD[2][2] + deltaT * (matrixD[2][3] * wx - matrixD[2][1] * wz - matrixD[1][2] * wUpC + matrixD[3][2] * wEC);
        D1[2][3] = matrixD[2][3] + deltaT * (matrixD[2][1] * wy - matrixD[2][2] * wx - matrixD[1][3] * wUpC + matrixD[3][3] * wEC);

        D1[3][1] = matrixD[3][1] + deltaT * (matrixD[3][2] * wz - matrixD[3][3] * wy + matrixD[1][1] * wNC - matrixD[2][1] * wEC);
        D1[3][2] = matrixD[3][2] + deltaT * (matrixD[3][3] * wx - matrixD[3][1] * wz + matrixD[1][2] * wNC - matrixD[2][2] * wEC);
        D1[3][3] = matrixD[3][3] + deltaT * (matrixD[3][1] * wy - matrixD[3][2] * wx + matrixD[1][3] * wNC - matrixD[2][3] * wEC);

        // end Puasson

        // normalize
        S = 1.0;

        if (num % 2 == 1) {
            //line
            S = Math.sqrt(D1[1][1] * D1[1][1] + D1[1][2] * D1[1][2] + D1[1][3] * D1[1][3]);
            matrixD[1][1] = D1[1][1] / S;
            matrixD[1][2] = D1[1][2] / S;
            matrixD[1][3] = D1[1][3] / S;

            S = Math.sqrt(D1[2][1] * D1[2][1] + D1[2][2] * D1[2][2] + D1[2][3] * D1[2][3]);
            matrixD[2][1] = D1[2][1] / S;
            matrixD[2][2] = D1[2][2] / S;
            matrixD[2][3] = D1[2][3] / S;

            S = Math.sqrt(D1[3][1] * D1[3][1] + D1[3][2] * D1[3][2] + D1[3][3] * D1[3][3]);
            matrixD[3][1] = D1[3][1] / S;
            matrixD[3][2] = D1[3][2] / S;
            matrixD[3][3] = D1[3][3] / S;
        } else {
            //column
            S = Math.sqrt(D1[1][1] * D1[1][1] + D1[2][1] * D1[2][1] + D1[3][1] * D1[3][1]);
            matrixD[1][1] = D1[1][1] / S;
            matrixD[2][1] = D1[2][1] / S;
            matrixD[3][1] = D1[3][1] / S;

            S = Math.sqrt(D1[1][2] * D1[1][2] + D1[2][2] * D1[2][2] + D1[3][2] * D1[3][2]);
            matrixD[1][2] = D1[1][2] / S;
            matrixD[2][2] = D1[2][2] / S;
            matrixD[3][2] = D1[3][2] / S;

            S = Math.sqrt(D1[1][3] * D1[1][3] + D1[2][3] * D1[2][3] + D1[3][3] * D1[3][3]);
            matrixD[1][3] = D1[1][3] / S;
            matrixD[2][3] = D1[2][3] / S;
            matrixD[3][3] = D1[3][3] / S;
        }

        // end normalize

        // calculate angles


        double C0 = Math.sqrt(matrixD[3][1] * matrixD[3][1] + matrixD[3][3] * matrixD[3][3]);

        double tetta = Math.atan(matrixD[3][2] / C0) / Math.PI * 180.0;
        double gamma = -Math.atan(matrixD[3][1] / matrixD[3][3]) / Math.PI * 180.0;

//        tetta = filter(tetta, tettaLast, filterT);
//        gamma = filter(gamma, gammaLast, filterT);
        psy = filter(psy, psyLast, 10000);

        psy = Math.atan(matrixD[1][2] / matrixD[2][2]) * Math.PI * 180.0;

//        System.out.println(String.format("gamma:\t%f\ttetta\t%f\tpsy:\t%f", gamma, tetta, psy));
//        main.pitchLabel.setText(String.format("Тангаж: %f", tetta));
//        main.rollLabel.setText(String.format("Крен: %f", gamma));


//        main.pitchSeries.addOrUpdate(new Millisecond(new Date(time.getMillisecond()+7*i)), tetta);
//        main.rollSeries.addOrUpdate(new Millisecond(new Date(time.getMillisecond()+7*i)), gamma);

//        gammaLast = gamma;
//        tettaLast = tetta;
//        psyLast = psy;


        if(calibrationCounter > 2000) {

            if (!isCalibrated){
                isCalibrated = true;
                gx0 = (int) - (gxSum / calibrationCounter);
                gy0 = (int) - (gySum / calibrationCounter);
                gz0 = (int) - (gzSum / calibrationCounter);
                System.out.println(String.format("calibrated: %d, %d, %d", gx0, gy0, gz0));
            }

            System.out.println(String.format("gamma:\t%f\ttetta\t%f\tpsy:\t%f", gamma, tetta, psy));
            main.pitchLabel.setText(String.format("Тангаж: %f", tetta));
            main.rollLabel.setText(String.format("Крен: %f", gamma));

            main.pitchSeries.addOrUpdate(new Millisecond(new Date(time.getMillisecond()+7*i)), tetta);
            main.rollSeries.addOrUpdate(new Millisecond(new Date(time.getMillisecond()+7*i)), gamma);

            gammaLast = gamma;
            tettaLast = tetta;
            psyLast = psy;

            Main.pitch = tetta;
            Main.roll = gamma;

        } else {

            calibrationCounter++;
            gxSum += jsonObject.getInt("gx");
            gySum += jsonObject.getInt("gy");
            gzSum += jsonObject.getInt("gz");

            axSum += jsonObject.getInt("ax");
            aySum += jsonObject.getInt("ay");
            azSum += jsonObject.getInt("az");

        }


    }

    private double filter(double value, double lastValue, double filterT){
        double result;
        result = ( value * gainFilter * deltaT + lastValue * filterT ) / (filterT + deltaT);

        return result;
    }


}

