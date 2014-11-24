import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.*;
import com.googlecode.javacpp.Loader;

import com.googlecode.javacv.cpp.opencv_core;
import static com.googlecode.javacv.cpp.opencv_highgui.*;
import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_contrib.*;
import static com.googlecode.javacv.cpp.opencv_objdetect.*;
import java.io.*;


class Map extends PastryMapper<ImageName, String, String, Integer>{
   	private final Integer queryLabel = new Integer(9);
	private final Integer one = new Integer(1);

   	public void map(ImageName key, String location, Context context) 
				throws IOException, InterruptedException {

		String trainingDir = "training";
	        IplImage testImage = cvLoadImage(key.toString());

	        File root = new File(trainingDir);

	        FilenameFilter pngFilter = new FilenameFilter() {
	            public boolean accept(File dir, String name) {
	                return name.toLowerCase().endsWith(".pgm");
	            }
	        };

	        File[] imageFiles = root.listFiles(pngFilter);

	        MatVector images = new MatVector(imageFiles.length);

	        int[] labels = new int[imageFiles.length];

	        int counter = 0;
	        int label;

	        IplImage img;
	        IplImage grayImg;

	        for (File image : imageFiles) {
	            img = cvLoadImage(image.getAbsolutePath());

	            label = Integer.parseInt(image.getName().split("\\-")[0]);

	            grayImg = IplImage.create(img.width(), img.height(), IPL_DEPTH_8U, 1);

	            cvCvtColor(img, grayImg, CV_BGR2GRAY);

	            images.put(counter, grayImg);

	            labels[counter] = label;

	            counter++;
	        }

	        IplImage greyTestImage = IplImage.create(testImage.width(), 
							testImage.height(), IPL_DEPTH_8U, 1);

	        FaceRecognizer faceRecognizer = createFisherFaceRecognizer();
	        // FaceRecognizer faceRecognizer = createEigenFaceRecognizer();
	        // FaceRecognizer faceRecognizer = createLBPHFaceRecognizer()

	        faceRecognizer.train(images, labels);

	        cvCvtColor(testImage, greyTestImage, CV_BGR2GRAY);

	        Integer predictedLabel = new Integer(faceRecognizer.predict(greyTestImage));

	        //System.out.println("Predicted label: " + predictedLabel);
		if (predictedLabel.equals(queryLabel)) {
			context.write(location, one);
		}
  	}
}
  
class Reduce extends PastryReducer<String,Integer,String,Integer> {

    private Integer result;

    public void reduce(String key, Iterable<Integer> values, 
                       Context context
                       ) throws IOException, InterruptedException {
      int sum = 0;
      for (Integer val : values) {
        sum += val;  
      }
      result = sum;

      context.write(key, result);
    }
}

public class FaceRecog {
 	public void setup (Map m) throws Exception {
    		m.setInputKeyClass(ImageName.class);
    		m.setInputValueClass(String.class);
    		m.setOutputKeyClass(String.class);
    		m.setOutputValueClass(Integer.class);
  	}
}
