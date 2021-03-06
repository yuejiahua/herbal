package experiment.percongress;

import static core.AbstractExperiment.max_iters;
import core.AbstractModel;
import core.AbstractSampler.InitialState;
import data.Congress;
import data.TextDataset;
import data.Vote;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.apache.commons.cli.ParseException;
import sampling.likelihood.CascadeDirMult.PathAssumption;
import sampling.util.SparseCount;
import util.CLIUtils;
import util.IOUtils;
import util.SparseVector;
import util.govtrack.GTLegislator;
import votepredictor.textidealpoint.AbstractTextIdealPoint.WordWeightType;
import votepredictor.AbstractVotePredictor;
import votepredictor.LexicalIdealPoint;
import votepredictor.LexicalSNLDAIdealPoint;
import votepredictor.SLDAIdealPoint;
import votepredictor.SLDAMultIdealPoint;
import votepredictor.SNLDAIdealPoint;
import votepredictor.baselines.AuthorTFIDFNN;
import votepredictor.baselines.AuthorTFNN;
import votepredictor.baselines.LogisticRegression;
import votepredictor.baselines.LogisticRegression.OptType;
import votepredictor.baselines.LogisticRegression.NormalizeType;

/**
 *
 * @author vietan
 */
public class HeldoutAuthorPredExpt extends VotePredExpt {

    @Override
    public String getConfiguredExptFolder() {
        return "author-heldout-" + numFolds + "-" + teRatio;
    }

    @Override
    public void run() {
        if (verbose) {
            logln("Running ...");
        }
        ArrayList<Integer> runningFolds = new ArrayList<Integer>();
        if (cmd.hasOption("fold")) {
            String foldList = cmd.getOptionValue("fold");
            for (String f : foldList.split(",")) {
                runningFolds.add(Integer.parseInt(f));
            }
        }

        loadFormattedData();

        setupSampling();

        File configureFolder = new File(new File(experimentPath, congressNum),
                getConfiguredExptFolder());

        for (int ff = 0; ff < numFolds; ff++) {
            if (!runningFolds.isEmpty() && !runningFolds.contains(ff)) {
                continue;
            }
            if (verbose) {
                logln("--- Running fold " + ff);
            }

            File foldFolder = new File(configureFolder, "fold-" + ff);
            IOUtils.createFolder(foldFolder);

            inputCrossValidatedData(ff);

            runModel(foldFolder);
        }

        evaluate();
    }

    public void analyze() {
        if (verbose) {
            logln("Analyzing ...");
        }
        ArrayList<Integer> runningFolds = new ArrayList<Integer>();
        if (cmd.hasOption("fold")) {
            String foldList = cmd.getOptionValue("fold");
            for (String f : foldList.split(",")) {
                runningFolds.add(Integer.parseInt(f));
            }
        }

        loadFormattedData();

        setupSampling();

        File configureFolder = new File(new File(experimentPath, congressNum),
                getConfiguredExptFolder());

        for (int ff = 0; ff < numFolds; ff++) {
            if (!runningFolds.isEmpty() && !runningFolds.contains(ff)) {
                continue;
            }
            if (verbose) {
                logln("--- Running fold " + ff);
            }

            File foldFolder = new File(configureFolder, "fold-" + ff);
            IOUtils.createFolder(foldFolder);

            inputCrossValidatedData(ff);

            analyzeErrorMultipleModels(foldFolder);
        }
    }

    /**
     * Run a model.
     *
     * @param outputFolder Output folder
     */
    @Override
    protected void runModel(File outputFolder) {
        int[][] voteTextWords = voteDataset.getWords();

        // get words of training bills
        trainVoteWords = new int[debateVoteData.getVoteVocab().size()][];
        trainVoteTopics = new int[debateVoteData.getVoteVocab().size()];

        List<String> billIdList = Arrays.asList(billData.getDocIds());
        for (int bb = 0; bb < trainVoteWords.length; bb++) {
            String keyvote = debateVoteData.getVoteVocab().get(bb);
            String billId = voteToBillMapping.get(keyvote);
            int idx = billIdList.indexOf(billId);

            trainVoteWords[bb] = concatArray(billData.getWords()[idx], voteTextWords[bb]);
            trainVoteTopics[bb] = billData.getTopics()[idx];
        }
        String model = CLIUtils.getStringArgument(cmd, "model", "random");
        switch (model) {
            case "random":
                runRandom(outputFolder);
                break;
            case "log-reg":
                runLogisticRegressors(outputFolder);
                break;
            case "author-tf-nn":
                runAuthorTFNN(outputFolder);
                break;
            case "author-tf-idf-nn":
                runAuthorTFIDFNN(outputFolder);
                break;
            case "bayesian-mult-ideal-point":
                runBayesianMultIdealPoint(outputFolder);
                break;
            case "lexical-ideal-point":
                runLexicalIdealPoint(outputFolder);
                break;
            case "slda-ideal-point":
                runSLDAIdealPoint(outputFolder);
                break;
            case "lexical-slda-ideal-point":
                runLexicalSLDAIdealPoint(outputFolder);
                break;
            case "hier-mult-tipm":
                runHierMultTIPM(outputFolder);
                break;
            case "hier-mult-shdp":
                runHierMultSHDP(outputFolder);
                break;
            case "hybrid-slda-ideal-point":
                runHybridSLDAIdealPoint(outputFolder);
                break;
            case "hybrid-slda-multiple-ideal-point":
                runHybridSLDAMultipleIdealPoint(outputFolder);
                break;
            case "slda-mult-ideal-point":
                runSLDAMultIdealPoint(outputFolder);
                break;
            case "recursive-slda-ideal-point":
                runRecursiveSLDAIdealPoint(outputFolder);
                break;
            case "snlda-ideal-point":
                runSNLDAIdealPoint(outputFolder);
                break;
            case "lexical-snlda-ideal-point":
                runLexicalSNLDAIdealPoint(outputFolder);
                break;
            case "hybrid-snlda-ideal-point":
                runHybridSNLDAIdealPoint(outputFolder);
                break;
            case "snlda-mult-ideal-point":
                runSNLDAMultIdealPoint(outputFolder);
                break;
            case "snhdp-ideal-point":
                runSNHDPIdealPoint(outputFolder);
                break;
            case "hybrid-snhdp-ideal-point":
                runHybridSNHDPIdealPoint(outputFolder);
                break;
            case "combine":
                combineModel(outputFolder);
                combineModelAuthorDependent(outputFolder);
                break;
            case "none":
                logln("Doing nothing :D");
                break;
            default:
                throw new RuntimeException("Model " + model + " not supported");
        }
    }

    protected void runLogisticRegressors(File outputFolder) {
        if (verbose) {
            logln("--- --- Running logistic regressors ...");
        }

        StringBuilder basename = new StringBuilder("logreg");

        if (cmd.hasOption("party")) {
            basename.append("-party");
        }

        LexicalIdealPoint lip = new LexicalIdealPoint();
        if (cmd.hasOption("lip")) {
            basename.append("-lip");

            double rho = 0.1;
            double sigma = 0.5;
            double lambda = 5;
            boolean tfidf = true;
            int maxIters = 10000;
            lip.configure(outputFolder.getAbsolutePath(),
                    debateVoteData.getWordVocab().size(), rho, sigma, lambda,
                    maxIters, tfidf);
        }

        // Lexical SNLDA
        LexicalSNLDAIdealPoint lexsnlda = new LexicalSNLDAIdealPoint();
        if (cmd.hasOption("lexicalsnlda")) {
            basename.append("-lexical-snlda");

            double[][] issuePhis;
            if (cmd.hasOption("K")) {
                int V = debateVoteData.getWordVocab().size();
                int K = Integer.parseInt(cmd.getOptionValue("K"));
                issuePhis = new double[K][V];
                for (int kk = 0; kk < K; kk++) {
                    Arrays.fill(issuePhis[kk], 1.0 / V);
                }
            } else {
                issuePhis = estimateIssues();
            }
            int J = 3;
            double[] alphas = new double[]{0.1, 0.1};
            double[] betas = new double[]{1, 0.5, 0.1};
            double[] gamma_means = new double[]{0.2, 0.2};
            double[] gamma_scales = new double[]{100, 10};
            double[] sigmas = new double[]{0, 2.5, 5};
            double sigma = 2.5;
            double rho = 0.05;
            double lambda = 2.5;
            boolean hasRootTopic = cmd.hasOption("roottopic");
            lexsnlda.configure(outputFolder.getAbsolutePath(),
                    debateVoteData.getWordVocab().size(), J,
                    issuePhis, alphas, betas, gamma_means, gamma_scales,
                    rho, sigmas, sigma, lambda, hasRootTopic,
                    InitialState.RANDOM, PathAssumption.MAXIMAL, false,
                    500, 1000, 50, 50);
            lexsnlda.inputModel(lexsnlda.getFinalStateFile().getAbsolutePath());
        }

        // ============================= SLDA ==================================
        SLDAMultIdealPoint sldaM = new SLDAMultIdealPoint();
        if (cmd.hasOption("sldamult")) {
            basename.append("-slda_mult");
            int K = 25;
            double alpha = 0.1;
            double beta = 0.1;
            double etaL2 = 0.1;
            double l1 = 0.0;
            double l2 = 0.1;
            double lexl1 = CLIUtils.getDoubleArgument(cmd, "lexl1", 0.0);
            double lexl2 = CLIUtils.getDoubleArgument(cmd, "lexl2", 2.5);
            boolean pOpt = true;
            InitialState iState = InitialState.PRESET;
//            sldaM.configure(outputFolder.getAbsolutePath(),
//                    debateVoteData.getWordVocab().size(), K,
//                    alpha, beta, etaL2, l1, l2, lexl1, lexl2,
//                    iState, pOpt,
//                    burn_in, max_iters, sample_lag, report_interval);
            sldaM.inputModel(sldaM.getFinalStateFile().getAbsolutePath());
        }

        SLDAIdealPoint slda = new SLDAIdealPoint();
        if (cmd.hasOption("slda")) {
            basename.append("-slda");

            int K = CLIUtils.getIntegerArgument(cmd, "K", 100);
            double alpha = CLIUtils.getDoubleArgument(cmd, "alpha", 0.1);
            double beta = CLIUtils.getDoubleArgument(cmd, "beta", 0.1);
            double mu = CLIUtils.getDoubleArgument(cmd, "mu", 0.0);
            double sigma = CLIUtils.getDoubleArgument(cmd, "sigma", 10);
            double rate_alpha = CLIUtils.getDoubleArgument(cmd, "rate-alpha", 1);
            double rate_eta = CLIUtils.getDoubleArgument(cmd, "rate-eta", 0.01);
            double rho = CLIUtils.getDoubleArgument(cmd, "rho", 1.0);
            String wordWeightTypeStr = CLIUtils.getStringArgument(cmd, "wwt", "none");
            WordWeightType wordWeightType;
            switch (wordWeightTypeStr) {
                case "none":
                    wordWeightType = WordWeightType.NONE;
                    break;
                case "tfidf":
                    wordWeightType = WordWeightType.TFIDF;
                    break;
                default:
                    throw new RuntimeException("Word weight type " + wordWeightTypeStr
                            + " not supported");
            }
            slda.configure(outputFolder.getAbsolutePath(),
                    debateVoteData.getWordVocab().size(), K,
                    alpha, beta, rho, mu, sigma, rate_alpha, rate_eta,
                    initState, paramOpt,
                    burn_in, max_iters, sample_lag, report_interval);
            slda.inputModel(slda.getFinalStateFile().getAbsolutePath());
        }

        // ============================= SNLDA =================================
        SNLDAIdealPoint snlda = new SNLDAIdealPoint();
        if (cmd.hasOption("snlda")) {
            basename.append("-snlda");

            double[][] issuePhis = estimateIssues();
            int J = 4;
            double[] alphas = new double[]{0.1, 0.1};
            double[] betas = new double[]{1.0, 0.5, 0.1};
            double[] gamma_means = new double[]{0.2, 0.2};
            double[] gamma_scales = new double[]{100, 10};
            double mu = 0.0;
            double sigma = 2.5;
            double rho = 0.5;
            WordWeightType wordWeightType = WordWeightType.TFIDF;
            boolean hasRootTopic = false;

            snlda.setVerbose(verbose);
            snlda.setDebug(debug);
            snlda.setLog(true);
            snlda.setReport(true);
            snlda.setWordVocab(debateVoteData.getWordVocab());
            snlda.setAuthorVocab(debateVoteData.getAuthorVocab());
            snlda.setLabelVocab(billData.getTopicVocab());

            PathAssumption pathAssumption = PathAssumption.MAXIMAL;
            String path = CLIUtils.getStringArgument(cmd, "path", "max");
            switch (path) {
                case "max":
                    pathAssumption = PathAssumption.MAXIMAL;
                    break;
                case "min":
                    pathAssumption = PathAssumption.MINIMAL;
                    break;
                default:
                    throw new RuntimeException("Path assumption " + path + " not supported");
            }

            snlda.configure(outputFolder.getAbsolutePath(),
                    debateVoteData.getWordVocab().size(), J,
                    issuePhis, alphas, betas, gamma_means, gamma_scales,
                    rho, mu, sigma, hasRootTopic,
                    initState, pathAssumption, paramOpt,
                    burn_in, max_iters, sample_lag, report_interval);
        }

        String optTypeStr = CLIUtils.getStringArgument(cmd, "opt-type", "lbfgs");
        String normTypeStr = CLIUtils.getStringArgument(cmd, "norm-type", "minmax");
        LogisticRegression lr = new LogisticRegression(basename.toString());
        String params = CLIUtils.getStringArgument(cmd, "params", "0.0,0.1");
        String[] sparams = params.split(",");
        NormalizeType normType = NormalizeType.NONE;
        switch (normTypeStr) {
            case "minmax":
                normType = NormalizeType.MINMAX;
                break;
            case "zscore":
                normType = NormalizeType.ZSCORE;
                break;
            case "tfidf":
                normType = NormalizeType.TFIDF;
                break;
            case "none":
                normType = NormalizeType.NONE;
                break;
            default:
                throw new RuntimeException("Normalization type " + normTypeStr
                        + " is not supported");
        }

        switch (optTypeStr) {
            case "lbfgs":
                double mu = Double.parseDouble(sparams[0]);
                double sigma = Double.parseDouble(sparams[1]);
                lr.configure(debateVoteData.getWordVocab().size(), OptType.LBFGS,
                        normType, mu, sigma);
                break;
            case "owlqn":
                double l1 = Double.parseDouble(sparams[0]);
                double l2 = Double.parseDouble(sparams[1]);
                lr.configure(debateVoteData.getWordVocab().size(), OptType.OWLQN,
                        normType, l1, l2);
                break;
            case "liblinear":
                double c = Double.parseDouble(sparams[0]);
                double epsilon = Double.parseDouble(sparams[1]);
                lr.configure(debateVoteData.getWordVocab().size(), OptType.LIBLINEAR,
                        normType, c, epsilon);
                break;
            default:
                throw new RuntimeException("OptType " + optTypeStr + " not supported");
        }
        File predFolder = new File(outputFolder, lr.getName());
        IOUtils.createFolder(predFolder);

        if (cmd.hasOption("train")) {
            ArrayList<SparseVector[]> addFeatures = new ArrayList<>();
            ArrayList<Integer> numFeatures = new ArrayList<>();

            if (cmd.hasOption("lip")) {
                lip.setupData(trainDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        votes,
                        trainAuthorIndices,
                        trainBillIndices,
                        trainVotes);
                File trResultFolder = new File(new File(outputFolder, lip.getSamplerFolder()),
                        TRAIN_PREFIX + RESULT_FOLDER);
                SparseVector[] authorFeatures = lip.inputAuthorFeatures(new File(trResultFolder, "author.features"));
                addFeatures.add(authorFeatures);
                numFeatures.add(1);
            }

            if (cmd.hasOption("lexicalsnlda")) {
                lexsnlda.setupData(trainDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        votes, trainAuthorIndices, trainBillIndices,
                        trainVotes);
                lexsnlda.inputFinalState();
                SparseVector[] authorFeatures = lexsnlda.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (cmd.hasOption("party")) {
                SparseVector[] authorParties = new SparseVector[trainAuthorIndices.size()];
                for (int aa = 0; aa < trainAuthorIndices.size(); aa++) {
                    authorParties[aa] = new SparseVector(2);
                    int author = trainAuthorIndices.get(aa);
                    String authorId = debateVoteData.getAuthorVocab().get(author);
                    String authorParty = debateVoteData.getAuthorProperty(authorId, GTLegislator.PARTY);
                    switch (authorParty) {
                        case "Republican":
                            authorParties[aa].set(0, 1.0);
                            break;
                        case "Democrat":
                            authorParties[aa].set(1, 1.0);
                            break;
                    }
                }
                addFeatures.add(authorParties);
                numFeatures.add(2);
            }

            if (cmd.hasOption("slda")) {
                slda.setupData(trainDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        votes, trainAuthorIndices, trainBillIndices,
                        trainVotes);
                slda.inputModel(slda.getFinalStateFile().getAbsolutePath());
                slda.inputAssignments(slda.getFinalStateFile().getAbsolutePath());
                SparseVector[] authorFeatures = slda.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (cmd.hasOption("sldamult")) {
                sldaM.setupData(trainDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        votes,
                        trainAuthorIndices,
                        trainBillIndices,
                        trainVotes);
                sldaM.inputAssignments(sldaM.getFinalStateFile().getAbsolutePath());
                SparseVector[] authorFeatures = sldaM.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (cmd.hasOption("snlda")) {
                snlda.setupData(trainDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        votes, trainAuthorIndices, trainBillIndices,
                        trainVotes);
                snlda.inputAssignments(snlda.getFinalStateFile().getAbsolutePath());
                SparseVector[] authorFeatures = snlda.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            lr.train(trainDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    votes,
                    trainAuthorIndices,
                    trainBillIndices,
                    trainVotes,
                    addFeatures, numFeatures);
            if (lr.getOptType() == OptType.LIBLINEAR) {
                File liblinearFolder = new File(predFolder, "liblinear");
                IOUtils.createFolder(liblinearFolder);
                lr.output(liblinearFolder);
            } else {
                lr.output(new File(predFolder, MODEL_FILE));
            }

            SparseVector[] predictions = lr.test(
                    trainDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    trainAuthorIndices,
                    trainVotes, addFeatures, numFeatures);
            File trResultFolder = new File(predFolder, TRAIN_PREFIX + RESULT_FOLDER);
            IOUtils.createFolder(trResultFolder);
            AbstractVotePredictor.outputPredictions(new File(trResultFolder, PREDICTION_FILE),
                    votes, predictions);
            AbstractModel.outputPerformances(new File(trResultFolder, RESULT_FILE),
                    AbstractVotePredictor.evaluateAll(votes, trainVotes, predictions));
        }

        if (cmd.hasOption("testauthor")) {
            ArrayList<SparseVector[]> addFeatures = new ArrayList<>();
            ArrayList<Integer> numFeatures = new ArrayList<>();

            if (cmd.hasOption("lip")) {
                lip.setupData(testDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        null,
                        testAuthorIndices,
                        testBillIndices,
                        testVotes);
                File teResultFolder = new File(new File(outputFolder, lip.getSamplerFolder()),
                        TEST_PREFIX + RESULT_FOLDER);
                SparseVector[] authorFeatures = lip.inputAuthorFeatures(new File(teResultFolder, "author.features"));
                addFeatures.add(authorFeatures);
                numFeatures.add(1);
            }

            if (cmd.hasOption("party")) {
                SparseVector[] authorParties = new SparseVector[testAuthorIndices.size()];
                for (int aa = 0; aa < testAuthorIndices.size(); aa++) {
                    authorParties[aa] = new SparseVector(2);
                    int author = testAuthorIndices.get(aa);
                    String authorId = debateVoteData.getAuthorVocab().get(author);
                    String authorParty = debateVoteData.getAuthorProperty(authorId, GTLegislator.PARTY);
                    switch (authorParty) {
                        case "Republican":
                            authorParties[aa].set(0, 1.0);
                            break;
                        case "Democrat":
                            authorParties[aa].set(1, 1.0);
                            break;
                    }
                }
                addFeatures.add(authorParties);
                numFeatures.add(2);
            }

            if (cmd.hasOption("lexicalsnlda")) {
                lexsnlda.setupData(testDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        null,
                        testAuthorIndices,
                        testBillIndices,
                        testVotes);
                File samplerFolder = new File(outputFolder, lexsnlda.getSamplerFolder());
                lexsnlda.inputAssignments(new File(samplerFolder,
                        "iter-predictions/iter-1000.zip").getAbsolutePath());
                SparseVector[] authorFeatures = lexsnlda.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (cmd.hasOption("slda")) {
                slda.setupData(testDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        null,
                        testAuthorIndices,
                        testBillIndices,
                        testVotes);
                slda.inputModel(slda.getFinalStateFile().getAbsolutePath());
                slda.inputAssignments(new File(new File(outputFolder, slda.getSamplerFolder()),
                        TEST_PREFIX + AssignmentFile).getAbsolutePath());
                SparseVector[] authorFeatures = slda.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (cmd.hasOption("snlda")) {
                File samplerFolder = new File(outputFolder, snlda.getSamplerFolder());
                snlda.setupData(testDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        null,
                        testAuthorIndices,
                        testBillIndices,
                        testVotes);
                snlda.inputAssignments(new File(samplerFolder,
                        TEST_PREFIX + AssignmentFile).getAbsolutePath());
                SparseVector[] authorFeatures = snlda.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (cmd.hasOption("sldamult")) {
                File samplerFolder = new File(outputFolder, sldaM.getSamplerFolder());
                sldaM.setupData(testDebateIndices,
                        debateVoteData.getWords(),
                        debateVoteData.getAuthors(),
                        null,
                        testAuthorIndices, null,
                        testVotes);
                sldaM.inputAssignments(new File(samplerFolder,
                        TEST_PREFIX + "assignments.zip").getAbsolutePath());
                SparseVector[] authorFeatures = sldaM.getAuthorFeatures();
                addFeatures.add(authorFeatures);
                numFeatures.add(authorFeatures[0].getDimension());
            }

            if (lr.getOptType() == OptType.LIBLINEAR) {
                File liblinearFolder = new File(predFolder, "liblinear");
                lr.input(liblinearFolder);
            } else {
                lr.input(new File(predFolder, MODEL_FILE));
            }
            SparseVector[] predictions = lr.test(
                    testDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    testAuthorIndices,
                    testVotes, addFeatures, numFeatures);
            File teResultFolder = new File(predFolder, TEST_PREFIX + RESULT_FOLDER);
            IOUtils.createFolder(teResultFolder);
            AbstractVotePredictor.outputPredictions(new File(teResultFolder, PREDICTION_FILE),
                    votes, predictions);
            AbstractModel.outputPerformances(new File(teResultFolder, RESULT_FILE),
                    AbstractVotePredictor.evaluateAll(votes, testVotes, predictions));
        }

        if (cmd.hasOption("analyzeerror")) {
            analyzeError(predFolder);
        }
    }

    protected void runAuthorTFNN(File outputFolder) {
        if (verbose) {
            logln("--- --- Running author TF-NN ...");
        }
        int K = CLIUtils.getIntegerArgument(cmd, "K", 5);
        AuthorTFNN pred = new AuthorTFNN("author-tf-K_" + K);
        pred.configure(debateVoteData.getWordVocab().size());
        File predFolder = new File(outputFolder, pred.getName());
        IOUtils.createFolder(predFolder);

        if (cmd.hasOption("train")) {
            pred.train(trainDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    votes,
                    trainAuthorIndices,
                    trainBillIndices,
                    trainVotes);
            pred.output(new File(predFolder, MODEL_FILE));
        }

        if (cmd.hasOption("testauthor")) {
            pred.input(new File(predFolder, MODEL_FILE));

            SparseVector[] predictions = pred.test(testDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    testAuthorIndices,
                    testVotes, K,
                    votes);
            File teResultFolder = new File(predFolder, TEST_PREFIX + RESULT_FOLDER);
            IOUtils.createFolder(teResultFolder);
            AbstractModel.outputPerformances(new File(teResultFolder, RESULT_FILE),
                    AbstractVotePredictor.evaluateAll(votes, testVotes, predictions));
        }
    }

    protected void runAuthorTFIDFNN(File outputFolder) {
        if (verbose) {
            logln("--- --- Running author TF-IDF-NN ...");
        }
        int K = CLIUtils.getIntegerArgument(cmd, "K", 5);
        AuthorTFIDFNN pred = new AuthorTFIDFNN("author-tf-idf-K_" + K);
        pred.configure(debateVoteData.getWordVocab().size());
        File predFolder = new File(outputFolder, pred.getName());
        IOUtils.createFolder(predFolder);

        if (cmd.hasOption("train")) {
            pred.train(trainDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    votes,
                    trainAuthorIndices,
                    trainBillIndices,
                    trainVotes);
            pred.output(new File(predFolder, MODEL_FILE));
        }

        if (cmd.hasOption("testauthor")) {
            pred.input(new File(predFolder, MODEL_FILE));

            SparseVector[] predictions = pred.test(testDebateIndices,
                    debateVoteData.getWords(),
                    debateVoteData.getAuthors(),
                    testAuthorIndices,
                    testVotes, K,
                    votes);
            File teResultFolder = new File(predFolder, TEST_PREFIX + RESULT_FOLDER);
            IOUtils.createFolder(teResultFolder);
            AbstractModel.outputPerformances(new File(teResultFolder, RESULT_FILE),
                    AbstractVotePredictor.evaluateAll(votes, testVotes, predictions));
        }
    }

    @Override
    public void preprocess() {
        if (verbose) {
            logln("Preprocessing ...");
        }
        try {
            loadFormattedData();
            outputCrossValidatedData();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Exception while preprocessing");
        }
    }

    /**
     * Output cross-validated data.
     */
    private void outputCrossValidatedData() throws Exception {
        File cvFolder = new File(processedDataFolder, getConfiguredExptFolder());
        IOUtils.createFolder(cvFolder);

        if (verbose) {
            logln("--- Outputing cross-validated data to " + cvFolder);
        }

        Random rand = new Random(1);

        int A = debateVoteData.getAuthorVocab().size();
        int D = debateVoteData.getWords().length;

        // list of author indices
        ArrayList<Integer> authorIndices = new ArrayList<>();
        for (int aa = 0; aa < A; aa++) {
            authorIndices.add(aa);
        }

        // count number of documents per author
        SparseCount authorDocCount = new SparseCount();
        for (int dd = 0; dd < D; dd++) {
            authorDocCount.increment(this.debateVoteData.getAuthors()[dd]);
        }

        if (verbose) {
            logln("--- --- # authors: " + A);
            logln("--- --- # authors with text: " + authorDocCount.size());
        }

        BufferedWriter writer;
        for (int ff = 0; ff < numFolds; ff++) {
            this.trainAuthorIndices = new ArrayList<>();
            this.testAuthorIndices = new ArrayList<>();
            Collections.shuffle(authorIndices);

            for (int ii = 0; ii < A; ii++) {
                int aa = authorIndices.get(ii);
                if (!authorDocCount.containsIndex(aa)) {
                    this.trainAuthorIndices.add(aa);
                } else {
                    if (rand.nextDouble() < teRatio) {
                        this.testAuthorIndices.add(aa);
                    } else {
                        this.trainAuthorIndices.add(aa);
                    }
                }
            }

            if (verbose) {
                logln("--- --- Fold " + ff);
                logln("--- --- # train authors: " + trainAuthorIndices.size());
                logln("--- --- # test authors: " + testAuthorIndices.size());
            }

            writer = IOUtils.getBufferedWriter(new File(cvFolder, "fold-" + ff + ".dat"));
            for (int aa = 0; aa < A; aa++) {
                if (trainAuthorIndices.contains(aa)) {
                    writer.write(aa + "\t" + TRAIN_POSFIX + "\n");
                } else {
                    writer.write(aa + "\t" + TEST_POSFIX + "\n");
                }
            }
            writer.close();
        }
    }

    /**
     * Input the cross-validated data from a fold.
     *
     * @param ff Fold number
     */
    protected void inputCrossValidatedData(int ff) {
        int A = debateVoteData.getAuthorVocab().size();
        int B = debateVoteData.getVoteVocab().size();
        int D = debateVoteData.getWords().length;

        this.trainAuthorIndices = new ArrayList<>();
        this.trainDebateIndices = new ArrayList<>();
        this.trainVotes = new boolean[A][B];
        this.trainBillIndices = null; // use all bills

        this.testAuthorIndices = new ArrayList<>();
        this.testDebateIndices = new ArrayList<>();
        this.testVotes = new boolean[A][B];
        this.testBillIndices = null; // use all bills

        File cvFolder = new File(processedDataFolder, getConfiguredExptFolder());
        try {
            if (verbose) {
                logln("--- Loading fold " + ff + " from " + cvFolder);
            }

            BufferedReader reader = IOUtils.getBufferedReader(new File(cvFolder,
                    "fold-" + ff + ".dat"));
            for (int aa = 0; aa < A; aa++) {
                String[] sline = reader.readLine().split("\t");
                if (aa != Integer.parseInt(sline[0])) {
                    throw new RuntimeException("Mismatch");
                }
                if (sline[1].equals(TRAIN_POSFIX)) {
                    this.trainAuthorIndices.add(aa);
                } else {
                    this.testAuthorIndices.add(aa);
                }
            }
            reader.close();

            for (int dd = 0; dd < D; dd++) {
                int aa = debateVoteData.getAuthors()[dd];
                if (trainAuthorIndices.contains(aa)) {
                    trainDebateIndices.add(dd);
                } else {
                    testDebateIndices.add(dd);
                }
            }

            int numTrainVotes = 0;
            int numTestVotes = 0;
            for (int ii = 0; ii < A; ii++) {
                for (int jj = 0; jj < B; jj++) {
                    if (debateVoteData.getVotes()[ii][jj] == Vote.MISSING) {
                        this.trainVotes[ii][jj] = false;
                        this.testVotes[ii][jj] = false;
                    } else if (trainAuthorIndices.contains(ii)) {
                        this.trainVotes[ii][jj] = true;
                        numTrainVotes++;
                    } else {
                        this.testVotes[ii][jj] = true;
                        numTestVotes++;
                    }
                }
            }

            if (verbose) {
                logln("--- --- train. # authors: " + trainAuthorIndices.size()
                        + ". # documents: " + trainDebateIndices.size()
                        + ". # votes: " + numTrainVotes);
                logln("--- --- test. # authors: " + testAuthorIndices.size()
                        + ". # documents: " + testDebateIndices.size()
                        + ". # votes: " + numTestVotes);
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            throw new RuntimeException("Exception while inputing fold " + ff
                    + " from " + cvFolder);
        }
    }

    public static void main(String[] args) {
        try {
            long sTime = System.currentTimeMillis();

            addOptions();

            cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                CLIUtils.printHelp(getHelpString(HeldoutAuthorPredExpt.class
                        .getName()), options);
                return;
            }

            verbose = cmd.hasOption("v");
            debug = cmd.hasOption("d");

            Congress.setVerbose(verbose);
            Congress.setDebug(debug);
            TextDataset.setDebug(debug);
            TextDataset.setVerbose(verbose);

            HeldoutAuthorPredExpt expt = new HeldoutAuthorPredExpt();
            expt.setup();
            String runMode = CLIUtils.getStringArgument(cmd, "run-mode", "run");
            switch (runMode) {
                case "run":
                    expt.run();
                    break;
                case "evaluate":
                    expt.evaluate();
                    break;
                case "create-cv":
                    expt.preprocess();
                    break;
                case "analyze":
                    expt.analyze();
                    break;
                default:
                    throw new RuntimeException("Run mode " + runMode + " is not supported");
            }

            // date and time
            DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
            Date dateobj = new Date();
            long eTime = (System.currentTimeMillis() - sTime) / 1000;
            System.out.println("Elapsed time: " + eTime + "s");
            System.out.println("End time: " + df.format(dateobj));
        } catch (RuntimeException | ParseException e) {
            e.printStackTrace();
        }
    }
}
