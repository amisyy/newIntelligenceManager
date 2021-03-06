package business.classify;

import com.hankcs.hanlp.classification.classifiers.AbstractClassifier;
import com.hankcs.hanlp.classification.corpus.Document;
import com.hankcs.hanlp.classification.corpus.IDataSet;
import com.hankcs.hanlp.classification.corpus.MemoryDataSet;
import com.hankcs.hanlp.classification.features.BaseFeatureData;
import com.hankcs.hanlp.classification.models.AbstractModel;
import com.hankcs.hanlp.classification.models.NaiveBayesModel;
import com.hankcs.hanlp.classification.utilities.MathUtility;
import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class MyClassifier extends AbstractClassifier {
    private NaiveBayesModel model;

    public MyClassifier(NaiveBayesModel naiveBayesModel)
    {
        this.model = naiveBayesModel;
    }

    public MyClassifier()
    {
        this(null);
    }

    public NaiveBayesModel getNaiveBayesModel()
    {
        return model;
    }

    public AbstractModel getModel()
    {
        return model;
    }

    public void train(String folderPath) throws IOException{
        IDataSet dataSet = new MemoryDataSet();
        dataSet.load(folderPath, "UTF-8");
        train(dataSet);
    }

    public void train(IDataSet dataSet)
    {
        //选择最佳特征
        BaseFeatureData featureData = selectFeatures(dataSet);

        //初始化分类器所用的数据
        model = new NaiveBayesModel();
        model.n = featureData.n; //样本数量
        model.d = featureData.featureCategoryJointCount.length; //特征数量

        model.c = featureData.categoryCounts.length; //类目数量
        model.logPriors = new TreeMap<Integer, Double>();

        int sumCategory;
        for (int category = 0; category < featureData.categoryCounts.length; category++)
        {
            sumCategory = featureData.categoryCounts[category];
            model.logPriors.put(category, Math.log((double) sumCategory / model.n));
        }

        //拉普拉斯平滑处理（又称加一平滑），这时需要估计每个类目下的实例
        Map<Integer, Double> featureOccurrencesInCategory = new TreeMap<Integer, Double>();

        Double featureOccSum;
        for (Integer category : model.logPriors.keySet())
        {
            featureOccSum = 0.0;
            for (int feature = 0; feature < featureData.featureCategoryJointCount.length; feature++)
            {

                featureOccSum += featureData.featureCategoryJointCount[feature][category];
            }
            featureOccurrencesInCategory.put(category, featureOccSum);
        }

        //对数似然估计
        int count;
        int[] featureCategoryCounts;
        double logLikelihood;
        for (Integer category : model.logPriors.keySet())
        {
            for (int feature = 0; feature < featureData.featureCategoryJointCount.length; feature++)
            {

                featureCategoryCounts = featureData.featureCategoryJointCount[feature];

                count = featureCategoryCounts[category];

                logLikelihood = Math.log((count + 1.0) / (featureOccurrencesInCategory.get(category) + model.d));
                if (!model.logLikelihoods.containsKey(feature))
                {
                    model.logLikelihoods.put(feature, new TreeMap<Integer, Double>());
                }
                model.logLikelihoods.get(feature).put(category, logLikelihood);
            }
        }
        model.catalog = dataSet.getCatalog().toArray();
        model.tokenizer = dataSet.getTokenizer();
        model.wordIdTrie = featureData.wordIdTrie;
    }

    public Map<String, Double> predict(String text) throws IllegalArgumentException, IllegalStateException
    {
        if (model == null)
        {
            throw new IllegalStateException("未训练模型！无法执行预测！");
        }
        if (text == null)
        {
            throw new IllegalArgumentException("参数 text == null");
        }

        //分词，创建文档
        Document doc = new Document(model.wordIdTrie, model.tokenizer.segment(text));

        return predict(doc);
    }

    @Override
    public double[] categorize(Document document) throws IllegalArgumentException, IllegalStateException
    {
        Integer category;
        Integer feature;
        Integer occurrences;
        Double logprob;

        double[] predictionScores = new double[model.catalog.length];
        for (Map.Entry<Integer, Double> entry1 : model.logPriors.entrySet())
        {
            category = entry1.getKey();
            logprob = entry1.getValue(); //用类目的对数似然初始化概率

            //对文档中的每个特征
            for (Map.Entry<Integer, int[]> entry2 : document.tfMap.entrySet())
            {
                feature = entry2.getKey();

                if (!model.logLikelihoods.containsKey(feature))
                {
                    continue; //如果在模型中找不到就跳过了
                }

                occurrences = entry2.getValue()[0]; //获取其在文档中的频次

                logprob += occurrences * model.logLikelihoods.get(feature).get(category); //将对数似然乘上频次
            }
            predictionScores[category] = logprob;
        }

        MathUtility.normalizeExp(predictionScores);
        return predictionScores;
    }

    /**
     * 统计特征并且执行特征选择，返回一个FeatureStats对象，用于计算模型中的概率
     *
     * @param dataSet
     * @return
     */
    protected BaseFeatureData selectFeatures(IDataSet dataSet)
    {
        //FeatureStats对象包含文档中所有特征及其统计信息
        BaseFeatureData featureData = new BaseFeatureData(dataSet); //执行统计
        featureData.wordIdTrie = new BinTrie<Integer>();
        String[] wordIdArray = dataSet.getLexicon().getWordIdArray();
        int p = 0;
        for(String wordId: wordIdArray){
            featureData.wordIdTrie.put(wordId,p++);
        }

        return featureData;
    }
}
