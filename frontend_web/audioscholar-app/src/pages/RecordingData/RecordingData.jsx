import axios from 'axios';
import React, { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';
import { FiHeadphones } from 'react-icons/fi';

const DownloadIcon = () => <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" /></svg>;
const ShareIcon = () => <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M8.684 13.342C8.886 12.938 9 12.482 9 12s-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.367 2.684 3 3 0 00-5.367-2.684z" /></svg>;

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
      const response = await axios.get(listUrl, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const allRecordings = response.data;
      const foundRecording = allRecordings.find(rec => rec.id === id);

      if (foundRecording) {
        setRecordingData(foundRecording);
        console.log("Found recording data:", foundRecording);
      } else {
        console.error(`Metadata with ID ${id} not found in the fetched list.`);
        setError("Recording metadata not found.");
      }
    } catch (err) {
      console.error('Error fetching recording list:', err);
      if (err.response) {
        if (err.response.status === 401 || err.response.status === 403) {
          setError("Session expired or not authorized. Please log in again.");
          localStorage.removeItem('AuthToken');
          navigate('/signin');
        } else {
          setError(`Failed to fetch recording list. Status: ${err.response.status}`);
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

    try {
      const summaryUrl = `${API_BASE_URL}api/recordings/${actualRecordingId}/summary`;
      const summaryPromise = axios.get(summaryUrl, { headers });

      const recommendationsUrl = `${API_BASE_URL}api/v1/recommendations/recording/${actualRecordingId}`;
      const recommendationsPromise = axios.get(recommendationsUrl, { headers });

      const [summaryResponse, recommendationsResponse] = await Promise.allSettled([
        summaryPromise,
        recommendationsPromise
      ]);

      if (summaryResponse.status === 'fulfilled') {
        setSummaryData(summaryResponse.value.data);
        console.log("Fetched summary:", summaryResponse.value.data);
      } else {
        console.error('Error fetching summary:', summaryResponse.reason);
        if (summaryResponse.reason.response?.status === 404) {
          setSummaryError('Summary not found for this recording.');
        } else if (summaryResponse.reason.response?.status === 403) {
          setSummaryError('Access denied to summary.');
        } else {
          setSummaryError('Failed to fetch summary.');
        }
      }

      if (recommendationsResponse.status === 'fulfilled') {
        setRecommendationsData(recommendationsResponse.value.data || []);
        console.log("Fetched recommendations:", recommendationsResponse.value.data);
      } else {
        console.error('Error fetching recommendations:', recommendationsResponse.reason);
        if (recommendationsResponse.reason.response?.status === 404) {
          setRecommendationsError('No recommendations found for this recording.');
        } else if (recommendationsResponse.reason.response?.status === 403) {
          setRecommendationsError('Access denied to recommendations.');
        } else {
          setRecommendationsError('Failed to fetch recommendations.');
        }
      }

    } catch (err) {
      console.error('Generic error fetching details:', err);
      setError('An unexpected error occurred while fetching details.');
    } finally {
      setSummaryLoading(false);
      setRecommendationsLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchRecordingData();
    }
  }, [id]);

  useEffect(() => {
    if (recordingData?.recordingId) {
      fetchDetails(recordingData.recordingId);
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

  const getStatusClass = (status) => {
    status = status?.toLowerCase();
    if (status === 'processed' || status === 'completed') {
      return 'bg-green-100 text-green-800';
    } else if (status === 'processing') {
      return 'bg-yellow-100 text-yellow-800';
    } else {
      return 'bg-gray-100 text-gray-800';
    }
  };

  const handleCopySummaryAndVocab = async () => {
    if (!summaryData) {
      alert("Summary data is not available to copy.");
      return;
    }

    let contentToCopy = "";

    if (summaryData.formattedSummaryText) {
      contentToCopy += "Summary Details:\n====================\n";
      contentToCopy += summaryData.formattedSummaryText + "\n\n";
    }

    if (summaryData.glossary && summaryData.glossary.length > 0) {
      contentToCopy += "Key Vocabulary:\n=================\n";
      summaryData.glossary.forEach(item => {
        contentToCopy += `${item.term}: ${item.definition}\n`;
      });
    }

    if (!contentToCopy) {
      alert("No content available to copy.");
      return;
    }

    try {
      await navigator.clipboard.writeText(contentToCopy);
      alert("Summary and Vocabulary copied to clipboard!");
    } catch (err) {
      console.error('Failed to copy text: ', err);
      alert("Failed to copy content. See console for details.");
    }
  };

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading recording data...</div>;
  }
  if (error) {
    return <div className="min-h-screen flex items-center justify-center text-red-500">{error}</div>;
  }
  if (!recordingData) {
    return <div className="min-h-screen flex items-center justify-center">Recording not found.</div>;
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
                <div className="flex items-center flex-wrap space-x-4 text-sm text-indigo-100 mb-4">
                  <span>{formatDate(recordingData.uploadTimestamp)}</span>
                </div>
                <div className="flex items-center space-x-2">
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-200 text-blue-800">
                    {recordingData.duration || 'Duration N/A'}
                  </span>
                  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getStatusClass(recordingData.status)}`}>
                    {recordingData.status || 'Unknown'}
                  </span>
                </div>
              </div>

              <div className="flex space-x-3 mt-4 md:mt-0 flex-shrink-0">
                <button
                  onClick={handleCopySummaryAndVocab}
                  className="inline-flex items-center bg-teal-500 hover:bg-teal-600 text-white font-medium py-2 px-4 rounded-md text-sm transition-all duration-200 ease-in-out shadow hover:shadow-md transform hover:-translate-y-0.5"
                >
                  <DownloadIcon /> Copy Summary & Notes
                </button>
              </div>
            </div>
          </div>

          {/* === Audio Playback Section === */}
          {recordingData.storageUrl ? (
            <div className="mb-8 bg-white rounded-lg shadow p-6 border border-gray-200">
              <h2 className="text-xl font-semibold text-gray-700 mb-3 flex items-center">
                <FiHeadphones className="mr-2 h-5 w-5 text-teal-600" /> Play Recording
              </h2>
              <audio
                controls
                src={recordingData.storageUrl} // Use storageUrl from fetched data
                className="w-full h-14 rounded-md bg-gray-100 shadow-inner"
                preload="metadata"
              >
                Your browser does not support the audio element.
              </audio>
              <p className="text-xs text-gray-500 mt-2">Streaming audio from secure storage.</p>
            </div>
          ) : (
            <div className="mb-8 bg-white rounded-lg shadow p-6 border border-gray-200">
               <h2 className="text-xl font-semibold text-gray-700 mb-3 flex items-center">
                  <FiHeadphones className="mr-2 h-5 w-5 text-gray-400" /> Play Recording
               </h2>
               <p className="text-gray-500 italic text-sm">Audio file not available for playback (missing storage URL).</p>
             </div>
          )}
          {/* === End Audio Playback Section === */}

          <div className="mb-6 border-b border-gray-300">
            <nav className="-mb-px flex space-x-8" aria-label="Tabs">
              <button
                onClick={() => setActiveTab('summary')}
                className={`whitespace-nowrap pb-4 px-1 border-b-2 font-medium text-sm transition-colors duration-150 ease-in-out ${activeTab === 'summary'
                  ? 'border-teal-500 text-teal-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-400'
                  }`}
              >
                Summary
              </button>
              <button
                onClick={() => setActiveTab('transcript')}
                className={`whitespace-nowrap pb-4 px-1 border-b-2 font-medium text-sm transition-colors duration-150 ease-in-out ${activeTab === 'transcript'
                  ? 'border-teal-500 text-teal-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-400'
                  }`}
              >
                Transcript
              </button>
            </nav>
          </div>

          <div className="bg-white rounded-lg shadow p-6 md:p-8">
            {activeTab === 'summary' && (
              <div>
                {summaryLoading && <p className="text-gray-600">Loading summary...</p>}
                {summaryError && <p className="text-red-500">{summaryError}</p>}
                {!summaryLoading && !summaryError && summaryData && (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                    <div className="md:col-span-2 space-y-6">
                      {summaryData.keyPoints && summaryData.keyPoints.length > 0 && (
                        <div>
                          <h3 className="font-semibold text-lg mb-2 text-gray-800">Key Points</h3>
                          <ul className="list-disc list-inside text-gray-700 space-y-1">
                            {summaryData.keyPoints.map((point, index) => (
                              <li key={index}>{point}</li>
                            ))}
                          </ul>
                        </div>
                      )}

                      <div>
                        <h3 className="font-semibold text-lg mb-2 text-gray-800">Summary Details</h3>
                        <div className="prose prose-sm max-w-none text-gray-700 leading-relaxed">
                          <ReactMarkdown>
                            {summaryData.formattedSummaryText || 'N/A'}
                          </ReactMarkdown>
                        </div>
                      </div>

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

                    <div className="space-y-6">
                      {summaryData.glossary && summaryData.glossary.length > 0 && (
                        <div>
                          <h3 className="font-semibold text-lg mb-2 text-gray-800">Key Vocabulary</h3>
                          <dl className="text-gray-700 space-y-3 overflow-y-auto pr-2 max-h-[100rem]">
                            {summaryData.glossary.map((item, index) => (
                              <div key={index}>
                                <dt className="font-medium text-gray-900">{item.term}</dt>
                                <dd className="text-sm">{item.definition}</dd>
                              </div>
                            ))}
                          </dl>
                        </div>
                      )}
                    </div>
                  </div>
                )}
                {!summaryLoading && !summaryError && !summaryData && (
                  <p className="text-gray-500">No summary data available for this recording.</p>
                )}
              </div>
            )}

            {activeTab === 'transcript' && (
              <div>
                <h2 className="text-xl font-semibold text-gray-800 mb-4">Transcript</h2>
                <pre className="whitespace-pre-wrap text-sm text-gray-700 bg-gray-50 p-4 rounded-md overflow-x-auto">
                  {recordingData?.transcriptText || 'Transcript not available.'}
                </pre>
              </div>
            )}
          </div>

          <div className="mt-10">
            <h2 className="text-2xl font-semibold text-gray-800 mb-5">Learning Recommendations</h2>
            {recommendationsLoading && <p className="text-gray-600">Loading recommendations...</p>}
            {recommendationsError && <p className="text-red-500">{recommendationsError}</p>}
            {!recommendationsLoading && !recommendationsError && recommendationsData.length > 0 && (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {recommendationsData.map((rec, index) => (
                  <a
                    key={rec.videoId || index}
                    href={`https://www.youtube.com/watch?v=${rec.videoId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-all duration-300 ease-in-out bg-white group transform hover:-translate-y-1"
                  >
                    {rec.thumbnailUrl ? (
                      <img src={rec.thumbnailUrl} alt={rec.title} className="w-full h-32 object-cover" />
                    ) : (
                      <div className="w-full h-32 bg-gray-100 flex items-center justify-center text-gray-400">
                        <span>No Thumbnail</span>
                      </div>
                    )}
                    <div className="p-3">
                      <h4 className="font-semibold text-sm text-gray-800 group-hover:text-teal-600 transition-colors duration-150" title={rec.title}>{rec.title}</h4>
                    </div>
                  </a>
                ))}
              </div>
            )}
            {!recommendationsLoading && !recommendationsError && recommendationsData.length === 0 && (
              <div className="bg-white rounded-lg shadow p-6 text-center text-gray-500">
                No learning recommendations available for this recording.
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};

export default RecordingData; 