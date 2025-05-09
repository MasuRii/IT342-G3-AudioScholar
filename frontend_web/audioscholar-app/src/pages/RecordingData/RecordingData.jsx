import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { FiAlertTriangle, FiCheckCircle, FiClock, FiHeadphones, FiLoader, FiUploadCloud } from 'react-icons/fi';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';

const DownloadIcon = () => <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" /></svg>;

const UPLOADING_STATUSES = ['UPLOADING_TO_STORAGE', 'UPLOAD_IN_PROGRESS', 'UPLOAD_PENDING'];
const UPLOAD_TIMEOUT_SECONDS = 10 * 60;
const TERMINAL_STATUSES = ['COMPLETED', 'COMPLETE', 'FAILED', 'PROCESSING_HALTED_UNSUITABLE_CONTENT', 'PROCESSING_HALTED_NO_SPEECH'];

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

  const isUploadingOrPending = UPLOADING_STATUSES.includes(statusUpper);
  const elapsedSeconds = uploadTimestamp?.seconds
    ? (Date.now() / 1000) - uploadTimestamp.seconds
    : 0;
  const isTimedOutUploadDisplay = isUploadingOrPending && elapsedSeconds > UPLOAD_TIMEOUT_SECONDS;

  if (isTimedOutUploadDisplay) {
    bgColor = 'bg-gray-100';
    textColor = 'text-gray-700';
    Icon = FiClock;
    displayStatus = 'Processing Upload';
    isSpinning = false;
    titleText = `Upload received ${Math.round(elapsedSeconds / 60)} mins ago, processing initiated. Status: ${originalStatus}`;
  } else {
    switch (statusUpper) {
      case 'COMPLETE':
      case 'COMPLETED':
        bgColor = 'bg-green-100';
        textColor = 'text-green-800';
        Icon = FiCheckCircle;
        displayStatus = 'Completed';
        break;
      case 'UPLOAD_PENDING':
      case 'UPLOAD_IN_PROGRESS':
      case 'UPLOADING_TO_STORAGE':
      case 'UPLOADED':
        bgColor = 'bg-blue-100';
        textColor = 'text-blue-800';
        Icon = FiUploadCloud;
        displayStatus = 'Uploading';
        isSpinning = true;
        break;
      case 'PROCESSING_QUEUED':
      case 'TRANSCRIBING':
      case 'PDF_CONVERTING':
      case 'PDF_CONVERTING_API':
      case 'TRANSCRIPTION_COMPLETE':
      case 'PDF_CONVERSION_COMPLETE':
      case 'SUMMARIZATION_QUEUED':
      case 'SUMMARIZING':
      case 'SUMMARY_COMPLETE':
      case 'RECOMMENDATIONS_QUEUED':
      case 'GENERATING_RECOMMENDATIONS':
      case 'PROCESSING':
        bgColor = 'bg-yellow-100';
        textColor = 'text-yellow-800';
        Icon = FiLoader;
        displayStatus = 'Processing';
        isSpinning = true;
        break;
      case 'FAILED':
      case 'PROCESSING_HALTED_NO_SPEECH':
      case 'PROCESSING_HALTED_UNSUITABLE_CONTENT':
        bgColor = 'bg-red-100';
        textColor = 'text-red-800';
        Icon = FiAlertTriangle;
        displayStatus = 'Failed';
        break;
      default:
        displayStatus = 'Unknown';
        if (statusUpper !== 'UNKNOWN') {
          console.warn('[Badge] Unknown recording status received:', originalStatus);
        }
        break;
    }
    titleText = (displayStatus === 'Failed' && failureReason)
      ? `${displayStatus}: ${failureReason}`
      : displayStatus;
    if (displayStatus !== originalStatus && originalStatus) {
      titleText += ` (Backend: ${originalStatus})`;
    }
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

const RecommendationCardImage = ({ thumbnailUrl, fallbackThumbnailUrl, title }) => {
  const [currentSrc, setCurrentSrc] = useState(fallbackThumbnailUrl || thumbnailUrl);
  const [hasError, setHasError] = useState(!(fallbackThumbnailUrl || thumbnailUrl));

  useEffect(() => {
    const initialSrc = fallbackThumbnailUrl || thumbnailUrl;
    setCurrentSrc(initialSrc);
    setHasError(!initialSrc);
  }, [thumbnailUrl, fallbackThumbnailUrl]);

  const handleError = () => {

    if (currentSrc === fallbackThumbnailUrl && thumbnailUrl) {
      setCurrentSrc(thumbnailUrl);
    } else {
      setHasError(true);
    }
  };

  if (hasError || !currentSrc) {
    return (
      <div className="w-full h-32 bg-gray-100 flex items-center justify-center text-gray-400 text-xs p-2">
        <span>(No Thumbnail)</span>
      </div>
    );
  }

  return (
    <img
      src={currentSrc}
      alt={title || 'Recommendation thumbnail'}
      className="w-full h-32 object-cover"
      onError={handleError}
    />
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
        .replace(/^- (.*)/gm, '- $1');
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

  const audioSrcToPlay = recordingData.storageUrl || recordingData.audioUrl;

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
                {summaryData?.topics && summaryData.topics.length > 0 && (
                  <div className="mt-2">
                    <div className="flex flex-wrap gap-2">
                      {summaryData.topics.map((topic, index) => (
                        <span key={index} className="bg-teal-600 bg-opacity-30 text-teal-50 text-xs font-medium px-2.5 py-0.5 rounded border border-teal-400">
                          {topic}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
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

          {audioSrcToPlay ? (
            <div className="mb-8 bg-white rounded-lg shadow-lg overflow-hidden border border-gray-200">
              <div className="bg-gradient-to-r from-teal-500 to-teal-600 text-white p-4 flex items-center justify-between">
                <h2 className="text-xl font-semibold flex items-center">
                  <FiHeadphones className="mr-2 h-5 w-5" /> Audio Player
                </h2>
                <StatusBadge recording={recordingData} />
              </div>

              <div className="p-6">
                <div className="bg-gray-50 p-5 rounded-lg border border-gray-100 shadow-inner">
                  <audio
                    controls
                    src={audioSrcToPlay}
                    className="w-full h-14 rounded-md focus:outline-none focus:ring-2 focus:ring-teal-500"
                    preload="metadata"
                  >
                    Your browser does not support the audio element.
                  </audio>

                  <div className="mt-4 flex justify-center">
                    <div className="bg-white p-2 rounded-md shadow-sm flex items-center justify-center">
                      <a href={audioSrcToPlay} download className="text-sm text-gray-700 flex items-center">
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1 text-teal-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                        </svg>
                        Download Audio
                      </a>
                    </div>
                  </div>
                </div>

                {!recordingData.transcriptText && recordingData.status !== 'failed' && recordingData.status !== 'processing_halted_unsuitable_content' && (
                  <div className="flex items-center bg-yellow-50 text-yellow-700 p-3 rounded-md mt-4 text-sm">
                    <FiClock className="mr-2 h-4 w-4" />
                    <p>Transcript processing may still be in progress. Please check back later.</p>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="mb-8 bg-white rounded-lg shadow-lg overflow-hidden border border-gray-200">
              <div className="bg-gradient-to-r from-gray-500 to-gray-600 text-white p-4">
                <h2 className="text-xl font-semibold flex items-center">
                  <FiHeadphones className="mr-2 h-5 w-5" /> Audio Player
                </h2>
              </div>
              <div className="p-8 text-center">
                <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gray-100 text-gray-400 mb-4">
                  <FiAlertTriangle className="h-6 w-6" />
                </div>
                <p className="text-gray-600 font-medium">Audio File Not Available</p>
                <p className="text-gray-500 text-sm mt-2">The audio file is missing or still processing. Please check back later.</p>
                <div className="mt-4">
                  <StatusBadge recording={recordingData} />
                </div>
              </div>
            </div>
          )}

          {(recordingData.pptxNhostUrl || recordingData.generatedPdfUrl || recordingData.convertApiPdfUrl) && (
            <div className="mb-8 bg-white rounded-lg shadow-lg overflow-hidden border border-gray-200">
              <div className="bg-gradient-to-r from-blue-500 to-blue-600 text-white p-4 flex items-center justify-between">
                <h2 className="text-xl font-semibold flex items-center">
                  <svg xmlns="http://www.w3.org/2000/svg" className="mr-2 h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  Related Files
                </h2>
              </div>

              <div className="p-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {(recordingData.convertApiPdfUrl || recordingData.generatedPdfUrl) && (
                    <a
                      href={recordingData.convertApiPdfUrl || recordingData.generatedPdfUrl}
                      download
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors duration-150"
                    >
                      <div className="w-10 h-10 flex-shrink-0 bg-red-100 rounded-full flex items-center justify-center mr-4">
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                        </svg>
                      </div>
                      <div className="flex-1">
                        <div className="font-medium text-gray-900">Presentation PDF</div>
                        <div className="text-sm text-gray-500">Download PDF document</div>
                      </div>
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                      </svg>
                    </a>
                  )}

                  {recordingData.pptxNhostUrl && (
                    <a
                      href={recordingData.pptxNhostUrl}
                      download
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors duration-150"
                    >
                      <div className="w-10 h-10 flex-shrink-0 bg-orange-100 rounded-full flex items-center justify-center mr-4">
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-orange-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2" />
                        </svg>
                      </div>
                      <div className="flex-1">
                        <div className="font-medium text-gray-900">PowerPoint Presentation</div>
                        <div className="text-sm text-gray-500">Download PPTX file {recordingData.originalPptxFileName ? `(${recordingData.originalPptxFileName})` : ''}</div>
                      </div>
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                      </svg>
                    </a>
                  )}
                </div>
              </div>
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

          <div className="bg-white rounded-lg shadow p-6 md:p-8 min-h-[300px] max-h-[700px] overflow-auto">
            {activeTab === 'summary' && (
              <div>
                {summaryLoading && <p className="text-gray-600 flex items-center"><FiLoader className="animate-spin mr-2" />Loading summary...</p>}
                {summaryError && !summaryLoading && (
                  <div className="text-center py-10 px-4">
                    <FiAlertTriangle className="mx-auto h-10 w-10 text-yellow-500 mb-3" />
                    <p className="text-gray-600 font-medium">Could not load summary</p>
                    <p className="text-sm text-gray-500 mt-1">{summaryError}</p>
                  </div>
                )}
                {!summaryLoading && !summaryError && summaryData && (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                    <div className="md:col-span-2 space-y-6 max-h-[600px] overflow-y-auto pr-2">
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

                    <div className="space-y-6 border-t md:border-t-0 md:border-l border-gray-200 pt-6 md:pt-0 md:pl-6 max-h-[600px] overflow-y-auto">
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
                  <pre className="whitespace-pre-wrap text-sm text-gray-700 bg-gray-50 p-4 rounded-md overflow-x-auto max-h-[550px] overflow-y-auto">
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
            {recommendationsLoading && <p className="text-gray-600 flex items-center"><FiLoader className="animate-spin mr-2" />Loading recommendations...</p>}
            {recommendationsError && !recommendationsLoading && (
              <div className="bg-white rounded-lg shadow p-6 text-center">
                <FiAlertTriangle className="mx-auto h-8 w-8 text-yellow-500 mb-2" />
                <p className="text-gray-600">{recommendationsError}</p>
              </div>
            )}
            {!recommendationsLoading && !recommendationsError && recommendationsData.length > 0 && (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {recommendationsData.map((rec, index) => {
                  return (
                    <a
                      key={rec.videoId || index}
                      href={rec.videoId ? `https://www.youtube.com/watch?v=${rec.videoId}` : '#'}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={`block border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-all duration-300 ease-in-out bg-white group transform hover:-translate-y-1 ${!rec.videoId ? 'opacity-70 cursor-default' : ''}`}
                      title={rec.title || 'Recommendation'}
                    >
                      <RecommendationCardImage
                        thumbnailUrl={rec.thumbnailUrl}
                        fallbackThumbnailUrl={rec.fallbackThumbnailUrl}
                        title={rec.title}
                      />
                      <div className="p-3">
                        <h4 className={`font-semibold text-sm text-gray-800 ${rec.videoId ? 'group-hover:text-teal-600' : ''} transition-colors duration-150 line-clamp-2`}>{rec.title || 'Untitled Recommendation'}</h4>
                      </div>
                    </a>
                  );
                })}
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