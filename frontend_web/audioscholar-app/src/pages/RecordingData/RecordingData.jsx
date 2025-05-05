import axios from 'axios';
import React, { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';
import { FiAlertTriangle, FiCheckCircle, FiClock, FiHeadphones, FiLoader, FiUploadCloud } from 'react-icons/fi';

const DownloadIcon = () => <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" /></svg>;

const UPLOADING_STATUSES = ['UPLOADING_TO_STORAGE', 'UPLOAD_IN_PROGRESS'];
const UPLOAD_TIMEOUT_SECONDS = 10 * 60;

const formatDuration = (seconds) => {
  if (seconds === null || typeof seconds !== 'number' || seconds < 0) return 'N/A';
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = Math.round(seconds % 60);
  return `${minutes}m ${remainingSeconds}s`;
};

const StatusBadge = ({ recording }) => {
    const { status, failureReason, uploadTimestamp } = recording;
    const originalStatus = status;
    const statusUpper = status?.toUpperCase() ?? 'UNKNOWN';
    let bgColor = 'bg-gray-100';
    let textColor = 'text-gray-800';
    let Icon = FiClock;
    let displayStatus = 'Unknown';
    let isSpinning = false;
    let titleText = '';

    const isUploading = UPLOADING_STATUSES.includes(statusUpper);
    const elapsedSeconds = uploadTimestamp?.seconds
        ? (Date.now() / 1000) - uploadTimestamp.seconds
        : 0;
    const isTimedOutUpload = isUploading && elapsedSeconds > UPLOAD_TIMEOUT_SECONDS;

    if (isTimedOutUpload) {
        bgColor = 'bg-gray-100';
        textColor = 'text-gray-700';
        Icon = FiClock;
        displayStatus = 'Processing Upload';
        isSpinning = false;
        titleText = `Upload received ${Math.round(elapsedSeconds / 60)} mins ago, processing initiated.`;
        console.log(`[Details Badge Timeout - ${recording.id}] Displaying timed-out upload status.`);
    } else {
        switch (statusUpper) {
            case 'COMPLETED':
            case 'PROCESSED':
                bgColor = 'bg-green-100';
                textColor = 'text-green-800';
                Icon = FiCheckCircle;
                displayStatus = 'Completed';
                break;
            case 'UPLOADING_TO_STORAGE':
            case 'UPLOAD_IN_PROGRESS':
                bgColor = 'bg-blue-100';
                textColor = 'text-blue-800';
                Icon = FiUploadCloud;
                displayStatus = 'Uploading';
                isSpinning = true;
                break;
            case 'PROCESSING':
            case 'PROCESSING_QUEUED':
            case 'TRANSCRIBING':
            case 'SUMMARIZING':
            case 'PDF_CONVERTING':
            case 'TRANSCRIPTION_COMPLETE':
            case 'PDF_CONVERSION_COMPLETE':
                bgColor = 'bg-yellow-100';
                textColor = 'text-yellow-800';
                Icon = FiLoader;
                displayStatus = 'Processing';
                isSpinning = true;
                break;
            case 'FAILED':
            case 'PROCESSING_HALTED_UNSUITABLE_CONTENT':
            case 'PROCESSING_HALTED_NO_SPEECH':
                bgColor = 'bg-red-100';
                textColor = 'text-red-800';
                Icon = FiAlertTriangle;
                displayStatus = 'Failed';
                break;
            default:
                displayStatus = 'Unknown';
                if (statusUpper !== 'UNKNOWN') {
                    console.warn('[Details Badge] Unknown recording status received:', originalStatus);
                }
                break;
        }
        titleText = (displayStatus === 'Failed' && failureReason)
                    ? `${displayStatus}: ${failureReason}`
                    : displayStatus;
    }

    return (
        <span
            title={titleText}
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${bgColor} ${textColor}`}
        >
             <Icon className={`mr-1 h-3 w-3 ${isSpinning ? 'animate-spin' : ''}`} />
            {displayStatus}
        </span>
    );
};

const RecordingData = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [recordingData, setRecordingData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [summaryData, setSummaryData] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState(null);

  const [recommendationsData, setRecommendationsData] = useState([]);
  const [recommendationsLoading, setRecommendationsLoading] = useState(false);
  const [recommendationsError, setRecommendationsError] = useState(null);

  const [activeTab, setActiveTab] = useState('summary');

  const fetchRecordingData = async () => {
    setLoading(true);
    setError(null);
    try {
      const token = localStorage.getItem('AuthToken');
      if (!token) {
        setError("User not authenticated. Please log in.");
        setLoading(false);
        navigate('/signin');
        return;
      }
      const listUrl = `${API_BASE_URL}api/audio/metadata`;
      console.log(`Fetching metadata list from: ${listUrl} to find ID: ${id}`);
      const response = await axios.get(listUrl, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const allRecordings = response.data;
      const foundRecording = allRecordings.find(rec => rec.id === id);

      if (foundRecording) {
        setRecordingData(foundRecording);
        console.log("Found recording metadata:", foundRecording);
      } else {
        console.error(`Metadata with ID ${id} not found in the fetched list.`);
        setError("Recording metadata not found or access denied.");
      }
    } catch (err) {
      console.error('Error fetching recording list:', err);
      if (err.response) {
        if (err.response.status === 401 || err.response.status === 403) {
          setError("Session expired or not authorized. Please log in again.");
          localStorage.removeItem('AuthToken');
          navigate('/signin');
        } else {
          setError(`Failed to fetch recording metadata. Status: ${err.response.status}`);
        }
      } else if (err.request) {
        setError("Network error: Could not reach the server.");
      } else {
        setError("An unexpected error occurred while fetching metadata.");
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchDetails = async (actualRecordingId) => {
    if (!actualRecordingId) {
      console.warn("Cannot fetch details, recordingId is missing from metadata.");
      setSummaryError("Cannot fetch summary: Internal data missing.");
      setRecommendationsError("Cannot fetch recommendations: Internal data missing.");
      return;
    }
    console.log(`Fetching details for recordingId: ${actualRecordingId}`);
    setSummaryLoading(true);
    setSummaryError(null);
    setRecommendationsLoading(true);
    setRecommendationsError(null);

    const token = localStorage.getItem('AuthToken');
    if (!token) {
      setError("User not authenticated. Please log in.");
      setSummaryLoading(false);
      setRecommendationsLoading(false);
      navigate('/signin');
      return;
    }
    const headers = { 'Authorization': `Bearer ${token}` };

    const summaryUrl = `${API_BASE_URL}api/recordings/${actualRecordingId}/summary`;
    let summaryStatus = null;
    try {
      console.log(`Fetching summary from: ${summaryUrl}`);
      const summaryResponse = await axios.get(summaryUrl, { headers });
      summaryStatus = summaryResponse.status;

      if (summaryResponse.status === 200) {
        setSummaryData(summaryResponse.data);
        console.log("Fetched summary (200 OK):", summaryResponse.data);
      } else if (summaryResponse.status === 202) {
          const message = summaryResponse.data?.message || "Processing is ongoing.";
          console.log(`Summary status 202 Accepted: ${message}`);
          setSummaryError(message);
          setSummaryData(null);
      } else {
           console.warn(`Unexpected success status for summary: ${summaryResponse.status}`);
          setSummaryError(`Unexpected status: ${summaryResponse.status}`);
          setSummaryData(null);
      }
    } catch (err) {
      console.error('Error fetching summary:', err);
      summaryStatus = err.response?.status;
      if (err.response) {
        const errorData = err.response.data;
        const message = errorData?.message || `Failed to fetch summary (Status: ${err.response.status})`;
        if (err.response.status === 404) {
          setSummaryError('Summary not found or recording processing failed/halted.');
        } else if (err.response.status === 403) {
          setSummaryError('Access denied to summary.');
        } else if (err.response.status === 401) {
           setError("Session expired or not authorized. Please log in again.");
           localStorage.removeItem('AuthToken');
           navigate('/signin');
        } else if (err.response.status === 500 && errorData?.status?.startsWith('PROCESSING_HALTED')) {
            setSummaryError(`Processing halted: ${errorData.message || 'Content unsuitable or no speech detected.'}`);
        } else if (err.response.status === 500 && errorData?.status === 'FAILED') {
            setSummaryError(`Processing failed: ${errorData.message || 'An error occurred during processing.'}`);
        }
         else {
          setSummaryError(message);
        }
      } else if (err.request) {
        setSummaryError("Network error fetching summary.");
      } else {
        setSummaryError("An unexpected error occurred fetching summary.");
      }
      setSummaryData(null);
    } finally {
      setSummaryLoading(false);
    }

    const shouldFetchRecommendations = summaryStatus === 200 || summaryStatus === 202;

    if (shouldFetchRecommendations) {
        const recommendationsUrl = `${API_BASE_URL}api/v1/recommendations/recording/${actualRecordingId}`;
        try {
          console.log(`Fetching recommendations from: ${recommendationsUrl}`);
          const recommendationsResponse = await axios.get(recommendationsUrl, { headers });

          if (recommendationsResponse.status === 200) {
             setRecommendationsData(recommendationsResponse.data || []);
             console.log("Fetched recommendations (200 OK):", recommendationsResponse.data);
          } else {
             console.warn(`Unexpected success status for recommendations: ${recommendationsResponse.status}`);
             setRecommendationsError(`Unexpected status: ${recommendationsResponse.status}`);
             setRecommendationsData([]);
          }
        } catch (err) {
          console.error('Error fetching recommendations:', err);
          if (err.response) {
             const message = err.response.data?.message || `Failed to fetch recommendations (Status: ${err.response.status})`;
             if (err.response.status === 404) {
                setRecommendationsError('No recommendations found for this recording.');
             } else if (err.response.status === 403) {
                setRecommendationsError('Access denied to recommendations.');
             } else if (err.response.status === 401) {
                setError("Session expired or not authorized. Please log in again.");
                localStorage.removeItem('AuthToken');
                navigate('/signin');
             } else {
                setRecommendationsError(message);
             }
          } else if (err.request) {
             setRecommendationsError("Network error fetching recommendations.");
          } else {
             setRecommendationsError("An unexpected error occurred fetching recommendations.");
          }
          setRecommendationsData([]);
        } finally {
          setRecommendationsLoading(false);
        }
    } else {
         console.log("Skipping recommendations fetch because summary was not successful or processing.");
        setRecommendationsError("Recommendations not available as processing did not complete successfully.");
        setRecommendationsLoading(false);
        setRecommendationsData([]);
    }
  };

  useEffect(() => {
    if (id) {
      console.log(`RecordingData component mounted or id changed: ${id}`);
      fetchRecordingData();
    } else {
        console.error("RecordingData: No ID found in params.");
        setError("Recording ID is missing.");
        setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (recordingData?.recordingId) {
      console.log(`Metadata loaded, fetching details for recordingId: ${recordingData.recordingId}`);
      fetchDetails(recordingData.recordingId);
    } else if (recordingData && !recordingData.recordingId) {
        console.error("Metadata found, but recordingId is missing:", recordingData);
        setSummaryError("Cannot fetch details: Critical recording identifier is missing.");
        setRecommendationsError("Cannot fetch details: Critical recording identifier is missing.");
    }
  }, [recordingData]);

  const formatDate = (timestamp) => {
    if (timestamp?.seconds) {
      return new Date(timestamp.seconds * 1000).toLocaleDateString(undefined, {
        year: 'numeric', month: 'long', day: '2-digit'
      });
    }
    return 'N/A';
  };

  const handleCopySummaryAndVocab = async () => {
    if (!summaryData) {
      alert("Summary data is not available to copy.");
      return;
    }

    let contentToCopy = `Title: ${recordingData?.title || 'Untitled Recording'}\n`;
    contentToCopy += `Date: ${formatDate(recordingData?.uploadTimestamp)}\n\n`;


    if (summaryData.formattedSummaryText) {
      contentToCopy += "Summary Details:\n====================\n";
      let plainSummary = summaryData.formattedSummaryText
          .replace(/### (.*)/g, '$1\n')
          .replace(/## (.*)/g, '$1\n')
          .replace(/# (.*)/g, '$1\n')
          .replace(/\*\*(.*?)\*\*/g, '$1')
          .replace(/__(.*?)__/g, '$1')
          .replace(/\*(.*?)\*/g, '$1')
          .replace(/_(.*?)_/g, '$1')
          .replace(/^\* (.*)/gm, '- $1')
          .replace(/^\- (.*)/gm, '- $1');
      contentToCopy += plainSummary.trim() + "\n\n";
    }

    if (summaryData.keyPoints && summaryData.keyPoints.length > 0) {
      contentToCopy += "Key Points:\n=============\n";
      summaryData.keyPoints.forEach(item => {
        contentToCopy += `- ${item}\n`;
      });
       contentToCopy += "\n";
    }


    if (summaryData.glossary && summaryData.glossary.length > 0) {
      contentToCopy += "Key Vocabulary:\n=================\n";
      summaryData.glossary.forEach(item => {
        contentToCopy += `${item.term}: ${item.definition}\n`;
      });
       contentToCopy += "\n";
    }

     if (summaryData.topics && summaryData.topics.length > 0) {
        contentToCopy += "Topics:\n=======\n";
        contentToCopy += summaryData.topics.join(', ') + "\n\n";
     }


    if (!contentToCopy) {
      alert("No content available to copy.");
      return;
    }

    try {
      await navigator.clipboard.writeText(contentToCopy.trim());
      alert("Recording details, summary, and vocabulary copied to clipboard!");
    } catch (err) {
      console.error('Failed to copy text: ', err);
      alert("Failed to copy content. Check console for details.");
    }
  };

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading recording metadata...</div>;
  }
  if (error) {
    return (
        <div className="min-h-screen flex flex-col bg-[#F0F4F8]">
             <title>AudioScholar - Error</title>
            <Header />
             <main className="flex-grow py-8 flex items-center justify-center">
                <div className="container mx-auto px-4 sm:px-6 lg:px-8 text-center bg-white p-10 rounded-lg shadow-md">
                    <h1 className="text-2xl font-bold text-red-600 mb-4">Error Loading Recording</h1>
                    <p className="text-gray-700">{error}</p>
                     <button
                       onClick={() => navigate('/recordings')}
                       className="mt-6 bg-[#2D8A8A] hover:bg-[#236b6b] text-white font-medium py-2 px-5 rounded-md transition"
                     >
                       Go to Recordings List
                     </button>
                </div>
             </main>
        </div>
    );
  }
  if (!recordingData) {
     return <div className="min-h-screen flex items-center justify-center">Recording metadata could not be loaded.</div>;
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#F0F4F8]">
      <title>{`AudioScholar - ${recordingData?.title || 'Recording Details'}`}</title>

      <Header />

      <main className="flex-grow py-8">
        <div className="container mx-auto px-4 sm:px-6 lg:px-8">

          <div className="bg-gradient-to-r from-[#1A365D] to-[#2D8A8A] text-white rounded-lg shadow-lg p-6 md:p-8 mb-8">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
              <div>
                <h1 className="text-3xl font-bold mb-2">{recordingData.title || 'Untitled Recording'}</h1>
                 <p className="text-sm text-indigo-200 mb-3">{recordingData.description || 'No description provided.'}</p>
                <div className="flex items-center flex-wrap gap-x-4 gap-y-2 text-sm text-indigo-100 mb-4">
                  <span>Uploaded: {formatDate(recordingData.uploadTimestamp)}</span>
                   <span>Duration: {formatDuration(recordingData.durationSeconds)}</span>
                   <StatusBadge recording={recordingData} />
                </div>
              </div>

              <div className="flex space-x-3 mt-4 md:mt-0 flex-shrink-0">
                <button
                  onClick={handleCopySummaryAndVocab}
                  disabled={!summaryData}
                  className={`inline-flex items-center bg-teal-500 text-white font-medium py-2 px-4 rounded-md text-sm transition-all duration-200 ease-in-out shadow hover:shadow-md transform hover:-translate-y-0.5 ${!summaryData ? 'opacity-50 cursor-not-allowed hover:bg-teal-500' : 'hover:bg-teal-600'}`}
                >
                  <DownloadIcon /> Copy Notes
                </button>
              </div>
            </div>
          </div>

          {recordingData.storageUrl ? (
            <div className="mb-8 bg-white rounded-lg shadow p-6 border border-gray-200">
              <h2 className="text-xl font-semibold text-gray-700 mb-3 flex items-center">
                <FiHeadphones className="mr-2 h-5 w-5 text-teal-600" /> Play Recording
              </h2>
              <audio
                controls
                src={recordingData.storageUrl}
                className="w-full h-14 rounded-md bg-gray-100 shadow-inner"
                preload="metadata"
              >
                Your browser does not support the audio element.
              </audio>
               {!recordingData.transcriptText && recordingData.status !== 'failed' && recordingData.status !== 'processing_halted_unsuitable_content' &&
                 <p className="text-xs text-yellow-600 mt-2">Transcript processing may still be in progress.</p>
               }
            </div>
          ) : (
             <div className="mb-8 bg-white rounded-lg shadow p-6 border border-gray-200">
               <h2 className="text-xl font-semibold text-gray-700 mb-3 flex items-center">
                  <FiHeadphones className="mr-2 h-5 w-5 text-gray-400" /> Play Recording
               </h2>
               <p className="text-gray-500 italic text-sm">Audio file not available for playback (missing storage URL or processing incomplete).</p>
             </div>
          )}

          <div className="mb-6 border-b border-gray-300">
            <nav className="-mb-px flex space-x-8" aria-label="Tabs">
              <button
                onClick={() => setActiveTab('summary')}
                className={`whitespace-nowrap pb-4 px-1 border-b-2 font-medium text-sm transition-colors duration-150 ease-in-out ${activeTab === 'summary'
                  ? 'border-teal-500 text-teal-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-400'
                  }`}
                disabled={!summaryLoading && !!summaryError}
              >
                Summary
              </button>
              <button
                onClick={() => setActiveTab('transcript')}
                className={`whitespace-nowrap pb-4 px-1 border-b-2 font-medium text-sm transition-colors duration-150 ease-in-out ${activeTab === 'transcript'
                  ? 'border-teal-500 text-teal-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-400'
                  }`}
                 disabled={!recordingData.transcriptText}
              >
                Transcript
              </button>
            </nav>
          </div>

          <div className="bg-white rounded-lg shadow p-6 md:p-8 min-h-[300px]">
            {activeTab === 'summary' && (
              <div>
                {summaryLoading && <p className="text-gray-600 flex items-center"><FiLoader className="animate-spin mr-2"/>Loading summary...</p>}
                {summaryError && !summaryLoading && (
                    <div className="text-center py-10 px-4">
                        <FiAlertTriangle className="mx-auto h-10 w-10 text-yellow-500 mb-3" />
                        <p className="text-gray-600 font-medium">Could not load summary</p>
                        <p className="text-sm text-gray-500 mt-1">{summaryError}</p>
                    </div>
                )}
                {!summaryLoading && !summaryError && summaryData && (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                    <div className="md:col-span-2 space-y-6">
                      {summaryData.formattedSummaryText && (
                         <div>
                            <h3 className="font-semibold text-lg mb-2 text-gray-800">Summary Details</h3>
                            <div className="prose prose-sm max-w-none text-gray-700 leading-relaxed">
                              <ReactMarkdown>
                                {summaryData.formattedSummaryText}
                              </ReactMarkdown>
                            </div>
                         </div>
                      )}
                       {!summaryData.formattedSummaryText && <p className="text-gray-500">Detailed summary is not available.</p>}
                    </div>

                    <div className="space-y-6 border-t md:border-t-0 md:border-l border-gray-200 pt-6 md:pt-0 md:pl-6">
                       {summaryData.keyPoints && summaryData.keyPoints.length > 0 && (
                        <div>
                          <h3 className="font-semibold text-lg mb-2 text-gray-800">Key Points</h3>
                          <ul className="list-disc list-inside text-gray-700 space-y-1 text-sm">
                            {summaryData.keyPoints.map((point, index) => (
                              <li key={index}>{point}</li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {summaryData.glossary && summaryData.glossary.length > 0 && (
                        <div>
                          <h3 className="font-semibold text-lg mb-2 text-gray-800">Key Vocabulary</h3>
                          <dl className="text-gray-700 space-y-3 text-sm">
                            {summaryData.glossary.map((item, index) => (
                              <div key={index}>
                                <dt className="font-medium text-gray-900">{item.term}</dt>
                                <dd>{item.definition}</dd>
                              </div>
                            ))}
                          </dl>
                        </div>
                      )}

                       {summaryData.topics && summaryData.topics.length > 0 && (
                        <div>
                          <h3 className="font-semibold text-lg mb-2 text-gray-800">Topics</h3>
                          <div className="flex flex-wrap gap-2">
                            {summaryData.topics.map((topic, index) => (
                              <span key={index} className="bg-indigo-100 text-indigo-800 text-xs font-medium px-2.5 py-0.5 rounded">
                                {topic}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                )}
                 {!summaryLoading && !summaryError && !summaryData && (
                    <p className="text-gray-500 text-center py-10">No summary data could be generated or retrieved for this recording.</p>
                 )}
              </div>
            )}

            {activeTab === 'transcript' && (
              <div>
                <h2 className="text-xl font-semibold text-gray-800 mb-4">Transcript</h2>
                {recordingData?.transcriptText ? (
                    <pre className="whitespace-pre-wrap text-sm text-gray-700 bg-gray-50 p-4 rounded-md overflow-x-auto max-h-[600px]">
                      {recordingData.transcriptText}
                    </pre>
                ) : (
                     <p className="text-gray-500">Transcript not available or still processing.</p>
                )}
              </div>
            )}
          </div>

          <div className="mt-10">
            <h2 className="text-2xl font-semibold text-gray-800 mb-5">Learning Recommendations</h2>
            {recommendationsLoading && <p className="text-gray-600 flex items-center"><FiLoader className="animate-spin mr-2"/>Loading recommendations...</p>}
             {recommendationsError && !recommendationsLoading && (
                 <div className="bg-white rounded-lg shadow p-6 text-center">
                    <FiAlertTriangle className="mx-auto h-8 w-8 text-yellow-500 mb-2" />
                    <p className="text-gray-600">{recommendationsError}</p>
                </div>
             )}
            {!recommendationsLoading && !recommendationsError && recommendationsData.length > 0 && (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {recommendationsData.map((rec, index) => (
                  <a
                    key={rec.videoId || index}
                    href={rec.videoId ? `https://www.youtube.com/watch?v=${rec.videoId}` : '#'}
                    target="_blank"
                    rel="noopener noreferrer"
                     className={`block border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-all duration-300 ease-in-out bg-white group transform hover:-translate-y-1 ${!rec.videoId ? 'opacity-70 cursor-default' : ''}`}
                     title={rec.title || 'Recommendation'}
                  >
                    {rec.thumbnailUrl ? (
                      <img src={rec.thumbnailUrl} alt={rec.title || 'Recommendation thumbnail'} className="w-full h-32 object-cover" />
                    ) : (
                      <div className="w-full h-32 bg-gray-100 flex items-center justify-center text-gray-400 text-xs p-2">
                        <span>(No Thumbnail)</span>
                      </div>
                    )}
                    <div className="p-3">
                      <h4 className={`font-semibold text-sm text-gray-800 ${rec.videoId ? 'group-hover:text-teal-600' : ''} transition-colors duration-150 line-clamp-2`}>{rec.title || 'Untitled Recommendation'}</h4>
                    </div>
                  </a>
                ))}
              </div>
            )}
            {!recommendationsLoading && !recommendationsError && recommendationsData.length === 0 && (
              <div className="bg-white rounded-lg shadow p-6 text-center text-gray-500">
                No specific learning recommendations were generated for this recording.
              </div>
            )}
          </div>

        </div>
      </main>
    </div>
  );
};

export default RecordingData; 