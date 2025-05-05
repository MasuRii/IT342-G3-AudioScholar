import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';
import { FiCheckCircle, FiFile, FiFileText, FiLoader, FiUpload, FiXCircle } from 'react-icons/fi';

const VALID_AUDIO_TYPES = [
  'audio/mpeg', 'audio/mp3',
  'audio/wav', 'audio/x-wav',
  'audio/aiff', 'audio/x-aiff',
  'audio/aac', 'audio/vnd.dlna.adts',
  'audio/ogg', 'application/ogg',
  'audio/flac', 'audio/x-flac',
];

const VALID_PPTX_TYPES = [
  'application/vnd.openxmlformats-officedocument.presentationml.presentation', // .pptx
  'application/vnd.ms-powerpoint', // .ppt
];

const VALID_AUDIO_EXTENSIONS = ['mp3', 'wav', 'aiff', 'aac', 'ogg', 'flac'];
const VALID_PPTX_EXTENSIONS = ['ppt', 'pptx'];

const Uploading = () => {
  const [selectedAudioFile, setSelectedAudioFile] = useState(null);
  const [audioFileName, setAudioFileName] = useState('');
  const [selectedPptxFile, setSelectedPptxFile] = useState(null);
  const [pptxFileName, setPptxFileName] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [pptxError, setPptxError] = useState('');
  const [loading, setLoading] = useState(false);
  const [audioPreviewUrl, setAudioPreviewUrl] = useState(null);
  const [success, setSuccess] = useState(null);
  const navigate = useNavigate();
  const audioFileInputRef = useRef(null);
  const pptxFileInputRef = useRef(null);

  useEffect(() => {
    const handleBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = 'Upload in progress. Are you sure you want to leave?';
      return 'Upload in progress. Are you sure you want to leave?';
    };

    if (loading) {
      window.addEventListener('beforeunload', handleBeforeUnload);
    } else {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    }

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [loading]);

  const handleAudioFileChange = (e) => {
    const file = e.target.files[0];
    setError('');
    setSuccess(null);

    if (!file) return;

    const fileExt = file.name.split('.').pop().toLowerCase();

    console.log(`Selected Audio File: ${file.name}, Type: ${file.type}, Ext: ${fileExt}`);

    if (
      !VALID_AUDIO_TYPES.includes(file.type.toLowerCase()) && 
      !VALID_AUDIO_EXTENSIONS.includes(fileExt)
    ) {
      setError(`Invalid audio file. Allowed types: ${VALID_AUDIO_EXTENSIONS.join(', ').toUpperCase()}`);
      setSelectedAudioFile(null);
      setAudioFileName('');
      setAudioPreviewUrl(null);
      if (audioFileInputRef.current) {
        audioFileInputRef.current.value = '';
      }
      return;
    }

    setSelectedAudioFile(file);
    setAudioFileName(file.name);
    if (!title || title === audioFileName.replace(/\.[^/.]+$/, "")) {
      setTitle(file.name.replace(/\.[^/.]+$/, ""));
    }
    
    const reader = new FileReader();
    reader.onloadend = () => {
      setAudioPreviewUrl(reader.result);
    };
    reader.onerror = () => {
      console.error("Error reading audio file for preview");
      setError("Could not generate audio preview.");
      setAudioPreviewUrl(null);
    };
    reader.readAsDataURL(file); 
  };

  const handlePptxFileChange = (e) => {
    const file = e.target.files[0];
    setPptxError('');
    setSuccess(null);

    if (!file) {
        setSelectedPptxFile(null);
        setPptxFileName('');
        if (pptxFileInputRef.current) {
           pptxFileInputRef.current.value = '';
        }
        return;
    }

    const fileExt = file.name.split('.').pop().toLowerCase();
    console.log(`Selected PowerPoint File: ${file.name}, Type: ${file.type}, Ext: ${fileExt}`);

    if (
      !VALID_PPTX_TYPES.includes(file.type.toLowerCase()) &&
      !VALID_PPTX_EXTENSIONS.includes(fileExt)
    ) {
      setPptxError(`Invalid PowerPoint file. Allowed types: ${VALID_PPTX_EXTENSIONS.join(', ').toUpperCase()}`);
      setSelectedPptxFile(null);
      setPptxFileName('');
      if (pptxFileInputRef.current) {
        pptxFileInputRef.current.value = '';
      }
      return;
    }

    setSelectedPptxFile(file);
    setPptxFileName(file.name);
  };

  const handleAudioClick = () => {
    if (!loading && audioFileInputRef.current) {
      audioFileInputRef.current.click();
    }
  };
  
  const handlePptxClick = () => {
    if (!loading && pptxFileInputRef.current) {
      pptxFileInputRef.current.click();
    }
  };

  const removeAudioFile = () => {
    setSelectedAudioFile(null);
    setAudioFileName('');
    if (title === audioFileName.replace(/\.[^/.]+$/, "")) {
      setTitle('');
    }
    setAudioPreviewUrl(null);
    if (audioFileInputRef.current) {
      audioFileInputRef.current.value = '';
    }
    setError('');
    setSuccess(null);
  };
  
  const removePptxFile = () => {
    setSelectedPptxFile(null);
    setPptxFileName('');
    if (pptxFileInputRef.current) {
      pptxFileInputRef.current.value = '';
    }
    setPptxError('');
    setSuccess(null);
  };

  const handleFileUpload = async (e) => {
    e.preventDefault();
    setError('');
    setPptxError('');
    setSuccess(null);

    if (!selectedAudioFile) {
      setError('Please select an audio file first');
      return;
    }
    if (!title.trim()) {
       setError('Please enter a title for the recording.');
       return;
    }

    setLoading(true);

    const AuthToken = localStorage.getItem('AuthToken');
    if (!AuthToken) {
      setError('Authentication token not found. Please sign in again.');
      setLoading(false);
      navigate('/signin');
      return;
    }

    const formData = new FormData();
    formData.append('audioFile', selectedAudioFile);
    formData.append('title', title.trim());
    if (description.trim()) {
      formData.append('description', description.trim());
    }
    if (selectedPptxFile) {
      formData.append('powerpointFile', selectedPptxFile);
      console.log("Appending PowerPoint file:", selectedPptxFile.name);
    }

    console.log("Preparing to upload file(s):", selectedAudioFile.name, "with title:", title.trim());

    try {
      const UPLOAD_URL = `${API_BASE_URL}api/audio/upload`;
      console.log(`Attempting to upload to: ${UPLOAD_URL}`);

      const response = await fetch(UPLOAD_URL, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${AuthToken}`,
        },
        body: formData,
      });

      if (!response.ok) {
        let errorMsg = `Upload failed with status: ${response.status}`;
        try {
          const errorBody = await response.json();
          errorMsg = errorBody.message || errorMsg;
        } catch (jsonError) {
          console.error("Error parsing error response body:", jsonError);
          errorMsg = `Upload failed: ${response.status} ${response.statusText}`;
        }
        console.error("Upload failed:", errorMsg);
        setError(errorMsg);
        setSuccess(null);
        if (response.status === 401) {
          console.log("Token expired or invalid, redirecting to sign in.");
          localStorage.removeItem('AuthToken');
          localStorage.removeItem('userId');
          navigate('/signin');
        }

      } else {
        const result = await response.json();
        console.log("Upload successful:", result);
        setSuccess('File(s) uploaded successfully! Processing has started.');
        removeAudioFile(); 
        removePptxFile(); 
        setTitle('');
        setDescription('');
      }

    } catch (err) {
      console.error("Error during file upload:", err);
      setError(`An error occurred during upload: ${err.message}`);
      setSuccess(null);
    } finally {
      setLoading(false);
    }
  };


  return (
    <div className="relative min-h-screen flex flex-col">
      {loading && (
        <div className="fixed inset-0 bg-black bg-opacity-50 z-50 flex flex-col items-center justify-center">
          <FiLoader className="animate-spin h-10 w-10 text-white mb-3" />
          <p className="text-white text-lg font-medium">Uploading, please wait...</p>
          <p className="text-gray-300 text-sm mt-1">Navigating away may interrupt the upload.</p>
        </div>
      )}

      <title>AudioScholar - Upload Recording</title>
      <Header />

      <main className="flex-grow flex items-center justify-center py-12 bg-gray-50">
        <div className="container mx-auto px-4 max-w-4xl">
          <div className="bg-white rounded-lg shadow-xl p-8 md:p-10">
            <h1 className="text-3xl font-bold text-gray-800 mb-6 text-center">Upload Recording</h1>

            <input
              type="file"
              id="audioFileInput"
              ref={audioFileInputRef}
              onChange={handleAudioFileChange}
              className="hidden"
              accept={VALID_AUDIO_TYPES.join(',')}
              disabled={loading}
            />
            <input
              type="file"
              id="pptxFileInput"
              ref={pptxFileInputRef}
              onChange={handlePptxFileChange}
              className="hidden"
              accept={VALID_PPTX_TYPES.join(',')}
              disabled={loading}
            />

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1 font-semibold">Audio File (Required)</label>
              <div
                onClick={handleAudioClick}
                className={`border-2 border-dashed border-gray-300 rounded-lg p-6 text-center transition-colors duration-200 ${loading ? 'opacity-60 cursor-not-allowed bg-gray-100' : 'cursor-pointer hover:bg-gray-100 hover:border-teal-400'}`}
              >
                {audioFileName ? (
                  <div className="flex flex-col items-center justify-center gap-2">
                    <FiFile className="h-8 w-8 text-teal-600" />
                    <p className="text-gray-800 font-medium text-sm truncate max-w-xs">{audioFileName}</p>
                    {!loading && (
                      <button
                        type="button"
                        onClick={(e) => { e.stopPropagation(); removeAudioFile(); }}
                        className="mt-1 text-red-600 hover:text-red-800 transition-colors duration-150 p-1 rounded-full hover:bg-red-100 inline-flex items-center gap-1 text-xs font-medium"
                        title="Remove audio file"
                      >
                        <FiXCircle className="h-4 w-4"/> Remove
                      </button>
                    )}
                  </div>
                ) : (
                  <div>
                    <FiUpload className="mx-auto h-10 w-10 text-gray-400" />
                    <p className="text-gray-600 mt-2 mb-1 text-sm">Click to select audio file</p>
                    <p className="text-xs text-gray-500">Supports: {VALID_AUDIO_EXTENSIONS.join(', ').toUpperCase()}</p>
                  </div>
                )}
              </div>
              {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
              {audioPreviewUrl && !loading && (
                <div className="mt-3 pt-3 border-t border-gray-100">
                  <h3 className="text-xs font-medium text-gray-600 mb-1">Audio Preview:</h3>
                  <audio controls src={audioPreviewUrl} className="w-full h-9" >
                    Your browser does not support the audio element.
                  </audio>
                </div>
              )}
            </div>

            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-1 font-semibold">PowerPoint File (Optional)</label>
               <div
                 onClick={handlePptxClick}
                 className={`border-2 border-dashed border-gray-300 rounded-lg p-6 text-center transition-colors duration-200 ${loading ? 'opacity-60 cursor-not-allowed bg-gray-100' : 'cursor-pointer hover:bg-gray-100 hover:border-teal-400'}`}
               >
                 {pptxFileName ? (
                   <div className="flex flex-col items-center justify-center gap-2">
                     <FiFileText className="h-8 w-8 text-blue-600" />
                     <p className="text-gray-800 font-medium text-sm truncate max-w-xs">{pptxFileName}</p>
                     {!loading && (
                       <button
                         type="button"
                         onClick={(e) => { e.stopPropagation(); removePptxFile(); }}
                         className="mt-1 text-red-600 hover:text-red-800 transition-colors duration-150 p-1 rounded-full hover:bg-red-100 inline-flex items-center gap-1 text-xs font-medium"
                         title="Remove PowerPoint file"
                       >
                         <FiXCircle className="h-4 w-4"/> Remove
                       </button>
                     )}
                   </div>
                 ) : (
                   <div>
                     <FiUpload className="mx-auto h-10 w-10 text-gray-400" />
                     <p className="text-gray-600 mt-2 mb-1 text-sm">Click to select PowerPoint file</p>
                     <p className="text-xs text-gray-500">Supports: {VALID_PPTX_EXTENSIONS.join(', ').toUpperCase()}</p>
                   </div>
                 )}
               </div>
               {pptxError && <p className="mt-2 text-sm text-red-600">{pptxError}</p>}
            </div>

            <form onSubmit={handleFileUpload} className="space-y-6 border-t pt-6">
              <div>
                <label htmlFor="audio-title" className="block text-sm font-medium text-gray-700 mb-1">
                  <span className="font-bold">Title:</span>
                  <span className="ml-1 text-gray-500">Enter title for your recording</span>
                </label>
                <input
                  type="text"
                  id="audio-title"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm disabled:bg-gray-100 disabled:cursor-not-allowed"
                  placeholder="Enter title for your recording (required)"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  disabled={loading}
                  required
                />
              </div>

              <div>
                <label htmlFor="audio-description" className="block text-sm font-medium text-gray-700 mb-1">
                  <span className="font-bold">Description (Optional):</span>
                </label>
                <textarea
                  id="audio-description"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 min-h-[80px] transition duration-150 ease-in-out shadow-sm disabled:bg-gray-100 disabled:cursor-not-allowed"
                  placeholder="Add a description for your recording"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div className="h-6">
                 {success && <p className="text-sm text-green-600 text-center flex items-center justify-center gap-1"><FiCheckCircle/>{success}</p>}
               </div>

              <button
                type="submit"
                disabled={!selectedAudioFile || loading || !title.trim()}
                className={`w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium flex items-center justify-center gap-2 transition-all duration-200 ease-in-out ${(!selectedAudioFile || loading || !title.trim()) ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#236b6b] hover:shadow-md transform hover:-translate-y-0.5'}`}
              >
                {loading ? <><FiLoader className="animate-spin h-5 w-5" /> Uploading...</> : <><FiUpload className="h-5 w-5"/> Upload Recording</>}
              </button>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Uploading;