/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.commandline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.archicontribs.modelrepository.authentication.CredentialsAuthenticator;
import org.archicontribs.modelrepository.authentication.CredentialsAuthenticator.SSHIdentityProvider;
import org.archicontribs.modelrepository.authentication.UsernamePassword;
import org.archicontribs.modelrepository.grafico.ArchiRepository;
import org.archicontribs.modelrepository.grafico.GraficoModelImporter;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IArchiRepository;
import org.archicontribs.modelrepository.merge.Strategy;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.osgi.util.NLS;

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;

/**
 * Command Line interface for loading a repository model and cloning an Archi model from online Repository
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository.commitModel
   --modelrepository.commitMessage "message"
   --modelrepository.pushModel
   --modelrepository.loadModel "cloneFolder"
   --modelrepository.userName "userName"
   --modelrepository.passFile "/pathtoPasswordFile"
   --modelrepository.identityFile "/pathtoIdentityFile"
   --modelrepository.pushConflicts "break/ours/theirs"
 * 
 * Archi -consoleLog -nosplash -p -application com.archimatetool.commandline.app
 *  --modelrepository.cloneModel "c:\dev\archi" 
 *  --modelrepository.loadModel "repo" 
 *  --modelrepository.userName "mike" 
 *  --modelrepository.passFile "pass.txt" 
 *  --modelrepository.commitModel 
 *  --modelrepository.commitMessage "moj commit" 
 *  --modelrepository.pushModel
 *  --modelrepository.pushConflicts "break/ours/theirs"
 * 
 * @author Phillip Beauvoir
 */
public class CommitModelToRepositoryProvider extends AbstractCommandLineProvider {

    static final String PREFIX = Messages.CommitModelToRepositoryProvider_0;
    
    static final String OPTION_COMMIT_MODEL = "modelrepository.commitModel"; //$NON-NLS-1$
    static final String OPTION_COMMIT_MESSAGE = "modelrepository.commitMessage";
    static final String OPTION_PUSH_MODEL = "modelrepository.pushModel";
    static final String OPTION_LOAD_MODEL = "modelrepository.loadModel";
    static final String OPTION_USERNAME = "modelrepository.userName"; //$NON-NLS-1$
    static final String OPTION_PASSFILE = "modelrepository.passFile"; //$NON-NLS-1$
    static final String OPTION_SSH_IDENTITY_FILE = "modelrepository.identityFile"; //$NON-NLS-1$
    static final String OPTION_BRANCH_NAME = "modelrepository.branchName";
    static final String OPTION_PUSH_CONFLICTS = "modelrepository.pushConflicts";
    
    public CommitModelToRepositoryProvider() {
    }
    
    @Override
    public void run(CommandLine commandLine) throws Exception {
        if(!hasCorrectOptions(commandLine)) {
            return;
        }
        logMessage("Uruchamiam commit/push");
        // loadModel folder must be set in both cases:
        // 1. Just load an existing local repository grafico model
        // 2. Clone online repository grafico model to folder specified in (1)
        
        String sFolder = commandLine.getOptionValue(OPTION_LOAD_MODEL);
        if(!StringUtils.isSet(sFolder)) {
            logError(NLS.bind(Messages.CommitModelToRepositoryProvider_1, OPTION_LOAD_MODEL));
            return;
        }
        
        File cloneFolder = new File(sFolder);
        // Commit
        if(commandLine.hasOption(OPTION_COMMIT_MODEL)) {
            IArchiRepository repo = new ArchiRepository(cloneFolder);
            String commitMessage = "Changes " + new Date();
            if(commandLine.hasOption(OPTION_COMMIT_MESSAGE)) {
            	commitMessage = commandLine.getOptionValue(OPTION_COMMIT_MESSAGE);
            }
            repo.commitChanges(commitMessage, false);
            logMessage(Messages.CommitModelToRepositoryProvider_5);
        }
        
        // Commit
        if(commandLine.hasOption(OPTION_PUSH_MODEL)) {
        	IArchiRepository repo = new ArchiRepository(cloneFolder);
        	String url = repo.getRemoteURL();
        	logMessage(url);
           // String url = commandLine.getOptionValue(OPTION_CLONE_MODEL);
            String username = commandLine.getOptionValue(OPTION_USERNAME);
            String password = getPasswordFromFile(commandLine);
            
            File identityFile = getSSHIdentityFile(commandLine);
            
            boolean isSSH = GraficoUtils.isSSH(url);
            
            if(!StringUtils.isSet(url)) {
                logError(Messages.CommitModelToRepositoryProvider_2);
                return;
            }
            
            if(!isSSH && !StringUtils.isSet(username)) {
                logError(Messages.CommitModelToRepositoryProvider_3);
                return;
            }
            
            if(!isSSH && !StringUtils.isSet(password)) {
                logError(Messages.CommitModelToRepositoryProvider_17);
                return;
            }
            
            if(isSSH && identityFile == null) {
                logError(Messages.CommitModelToRepositoryProvider_18);
                return;
            }
            
            logMessage("Pushing to repo");
            
            
            // Set this to return our details rather than using the defaults from App prefs
            CredentialsAuthenticator.setSSHIdentityProvider(new SSHIdentityProvider() {
                @Override
                public File getIdentityFile() {
                    return identityFile;
                }

                @Override
                public char[] getIdentityPassword() {
                    return password.toCharArray();
                }
            });
            
            PullResult result = repo.pullFromRemote(new UsernamePassword(username, password.toCharArray()), 
            		Strategy.getStrategyByName(commandLine.getOptionValue("OPTION_PUSH_CONFLICTS", "break"))
            		, null);
            if(result.isSuccessful()) {            
            	repo.pushToRemote(new UsernamePassword(username, password.toCharArray()), null);
            }else {
            	logError("Push error: " + result.getMergeResult().getMergeStatus());
            	logError("Conflicts :" + result.getMergeResult().getConflicts().keySet());
            }
            
            logMessage("Model pushed to repo");
        }
        
        loadModel(cloneFolder);
    }
    
    private IArchimateModel loadModel(File folder) throws IOException {
        GraficoModelImporter importer = new GraficoModelImporter(folder);
        IArchimateModel model = importer.importAsModel();
        
        if(model == null) {
            throw new IOException(NLS.bind(Messages.LoadModelFromRepositoryProvider_21, folder));
        }
        
        if(importer.getUnresolvedObjects() != null) {
            throw new IOException(Messages.LoadModelFromRepositoryProvider_8);
        }
        
        CommandLineState.setModel(model);
        
        return model;
    }

    private String getPasswordFromFile(CommandLine commandLine) throws IOException {
        String password = null;
        
        String path = commandLine.getOptionValue(OPTION_PASSFILE);
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.exists() && file.canRead()) {
                password = new String(Files.readAllBytes(Paths.get(file.getPath())));
                password = password.trim();
            }
        }

        return password;
    }
            
    private File getSSHIdentityFile(CommandLine commandLine) {
        String path = commandLine.getOptionValue(OPTION_SSH_IDENTITY_FILE);
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.exists() && file.canRead()) {
                return file;
            }
        }
        
        return null;
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_LOAD_MODEL).hasArg(false)
                .hasArg()
                .argName(Messages.CommitModelToRepositoryProvider_9)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_10, OPTION_LOAD_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_COMMIT_MODEL)
                .hasArg(false)
                .argName(Messages.CommitModelToRepositoryProvider_11)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_12, OPTION_COMMIT_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_COMMIT_MESSAGE)
                .hasArg()
                .argName(Messages.CommitModelToRepositoryProvider_11)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_12, OPTION_COMMIT_MESSAGE))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_PUSH_MODEL)
                .hasArg(false)
                .argName(Messages.CommitModelToRepositoryProvider_11)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_12, OPTION_PUSH_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_PUSH_CONFLICTS)
                .hasArg()
                .argName("pushConflicts")
                .desc("Conflicts options break/ours/theirs")
                .build();
        options.addOption(option);
        
        
        option = Option.builder()
                .longOpt(OPTION_BRANCH_NAME)
                .hasArg()
                .argName(Messages.CommitModelToRepositoryProvider_23)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_22, OPTION_BRANCH_NAME))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_USERNAME)
                .hasArg()
                .argName(Messages.CommitModelToRepositoryProvider_13)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_14, OPTION_COMMIT_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_PASSFILE)
                .hasArg()
                .argName(Messages.CommitModelToRepositoryProvider_15)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_16, OPTION_COMMIT_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_SSH_IDENTITY_FILE)
                .hasArg()
                .argName(Messages.CommitModelToRepositoryProvider_19)
                .desc(NLS.bind(Messages.CommitModelToRepositoryProvider_20, OPTION_COMMIT_MODEL))
                .build();
        options.addOption(option);

        return options;
    }
    
    private boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_COMMIT_MODEL) || commandLine.hasOption(OPTION_PUSH_MODEL);
    }
    
    @Override
    public int getPriority() {
        return PRIORITY_REPORT_OR_EXPORT - 1;
    }
    
    @Override
    protected String getLogPrefix() {
        return PREFIX;
    }
}
