import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Link } from 'react-router-dom'; // Import Link for "Back to Recordings"

const Summaryview = () => {
  const { recordingId } = useParams(); // Get the recordingId from the URL
  const navigate = useNavigate(); // For navigation (though Back uses Link)

  // State to hold the summary data
  const [summaryData, setSummaryData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('summary'); // State for active tab

  useEffect(() => {
    // Fetch summary data for the specific recordingId when the component mounts
    const fetchSummary = async () => {
      try {
        setLoading(true);
        // Replace with your actual API call to fetch summary by ID
        // Example: const data = await apiService.getSummary(recordingId);
        // setSummaryData(data);

        // --- Placeholder data based on the web screenshot (replace with API fetch) ---
         const placeholderData = {
            id: recordingId,
            title: 'Introduction to Machine Learning',
            course: 'CS401',
            instructor: 'Dr. Sarah Miller',
            date: '1/18/2024',
            status: 'Completed', // Based on the small tag next to the date
            keyPoints: [
              'Understanding basic ML concepts',
              'Types of machine learning algorithms',
              'Supervised vs Unsupervised learning',
              'Common applications of ML',
              'Model evaluation metrics',
            ],
            keyVocabulary: [
                { term: 'Supervised Learning', definition: 'A type of ML where the model is trained on labeled data.' },
                { term: 'Unsupervised Learning', definition: 'A type of ML where the model finds patterns in unlabeled data.' },
                { term: 'Neural Network', definition: 'A computing system inspired by biological neural networks.' },
                { term: 'Overfitting', definition: 'When a model learns the training data too well, including noise and outliers.' },
            ],
            detailedSummary: [
                {
                    heading: 'Introduction to ML Concepts',
                    content: 'Machine learning is a subset of Artificial Intelligence that focuses on developing algorithms that enable computers to learn from data without being explicitly programmed. The lecture covered fundamental concepts and their practical applications in modern technology.'
                },
                 {
                    heading: 'Types of Machine Learning',
                    content: 'We discussed three main types of machine learning: supervised learning, unsupervised learning, and reinforcement learning. Each type has its specific use cases and applications in real-world scenarios.'
                },
                 {
                    heading: 'Model Evaluation',
                    content: 'Various metrics for evaluating machine learning models were covered, including accuracy, precision, recall, and F1 score. The importance of choosing appropriate evaluation metrics based on the problem context was emphasized.'
                },
            ],
            practiceQuestions: [
                'What are the key differences between supervised and unsupervised learning?',
                'How do you choose appropriate evaluation metrics for a ML model?',
                'What are common challenges in implementing ML solutions?',
            ],
            transcript: 'This is a placeholder for the transcript content.', // Placeholder
            myNotes: 'This is a placeholder for your personal notes.', // Placeholder
          };
          setSummaryData(placeholderData);
        // --- End Placeholder data ---

        setLoading(false);
      } catch (err) {
        setError(err);
        setLoading(false);
      }
    };

    fetchSummary();
  }, [recordingId]); // Rerun effect if recordingId changes

  if (loading) {
    return <div className="text-center mt-8">Loading summary...</div>;
  }

  if (error) {
    return <div className="text-center mt-8 text-red-600">Error loading summary: {error.message}</div>;
  }

  if (!summaryData) {
       return <div className="text-center mt-8">No summary data found for ID: {recordingId}.</div>;
  }


  // Destructure data for easier access
  const {
      title,
      course,
      instructor,
      date,
      status,
      keyPoints,
      keyVocabulary,
      detailedSummary,
      practiceQuestions,
      transcript,
      myNotes
  } = summaryData;

  return (
    <div className="min-h-screen flex flex-col bg-gray-100">
      {/* Header based on web design */}
      <header className="bg-[#1A365D] text-white py-4 shadow-sm">
        <div className="container mx-auto px-4 flex justify-between items-center">
          <span className="text-2xl font-bold">AudioScholar</span> {/* Placeholder Logo/Title */}
          <Link to="/recordings" className="text-gray-300 hover:text-indigo-400 transition-colors">
            Back to Recordings
          </Link>
        </div>
      </header>

      {/* Main content area */}
      <main className="flex-grow container mx-auto px-4 py-8">
        <div className="bg-white rounded-lg shadow-md p-6">
          {/* Recording Title and Details */}
          <div className="mb-6 border-b pb-4">
            <h1 className="text-2xl font-bold text-gray-800 mb-2">{title}</h1>
            <div className="text-gray-600 text-sm flex items-center space-x-4">
              <span>{course} - {instructor}</span>
              <span>{date}</span>
               {/* Status tag */}
              {status && (
                 <span className={`px-2 py-0.5 text-xs font-semibold rounded-full ${
                    status === 'Completed'
                      ? 'bg-green-100 text-green-800'
                      : 'bg-yellow-100 text-yellow-800' // Adjust colors based on your design
                  }`}>
                    {status}
                  </span>
              )}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex justify-end space-x-4 mb-6">
            <button className="bg-[#2D8A8A] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition">
              Download Summary
            </button>
            <button className="bg-gray-300 text-gray-800 py-2 px-4 rounded-lg font-medium hover:bg-gray-400 transition">
              Share
            </button>
          </div>

          {/* Tabbed Interface */}
          <div className="border-b border-gray-200 mb-6">
            <nav className="-mb-px flex space-x-8">
              <button
                className={`whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm ${
                  activeTab === 'summary'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => setActiveTab('summary')}
              >
                Summary
              </button>
              <button
                className={`whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm ${
                  activeTab === 'transcript'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => setActiveTab('transcript')}
              >
                Transcript
              </button>
              <button
                className={`whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm ${
                  activeTab === 'myNotes'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => setActiveTab('myNotes')}
              >
                My Notes
              </button>
            </nav>
          </div>

          {/* Tab Content */}
          <div>
            {activeTab === 'summary' && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {/* Left Column */}
                <div className="md:col-span-2 space-y-6">
                  {/* Key Points */}
                  <section className="bg-gray-50 p-4 rounded-lg">
                    <h3 className="text-md font-semibold text-gray-800 mb-3">Key Points</h3>
                    <ul className="list-disc list-inside text-gray-700 text-sm space-y-1">
                      {keyPoints.map((point, index) => (
                        <li key={index}>{point}</li>
                      ))}
                    </ul>
                  </section>

                  {/* Detailed Summary Sections */}
                  {detailedSummary.map((section, index) => (
                    <section key={index} className="space-y-2">
                      <h3 className="text-md font-semibold text-gray-800">{section.heading}</h3>
                      <p className="text-gray-700 text-sm">{section.content}</p>
                    </section>
                  ))}
                </div>

                {/* Right Column */}
                <div className="md:col-span-1 space-y-6">
                   {/* Key Vocabulary */}
                  <section className="bg-gray-50 p-4 rounded-lg">
                    <h3 className="text-md font-semibold text-gray-800 mb-3">Key Vocabulary</h3>
                    <ul className="text-gray-700 text-sm space-y-2">
                      {keyVocabulary.map((vocab, index) => (
                        <li key={index}>
                            <strong className="block">{vocab.term}:</strong> {vocab.definition}
                        </li>
                      ))}
                    </ul>
                  </section>

                  {/* Practice Questions */}
                  <section className="bg-blue-50 p-4 rounded-lg">
                    <h3 className="text-md font-semibold text-gray-800 mb-3">Practice Questions</h3>
                    <ul className="list-disc list-inside text-gray-700 text-sm space-y-1">
                      {practiceQuestions.map((question, index) => (
                        <li key={index}>{question}</li>
                      ))}
                    </ul>
                  </section>
                </div>
              </div>
            )}

            {activeTab === 'transcript' && (
              <div className="bg-gray-50 p-4 rounded-lg text-gray-700">
                <h3 className="text-md font-semibold mb-3">Transcript</h3>
                <p>{transcript}</p> {/* Display transcript placeholder */}
                 {/* You would load and display the actual transcript here */}
              </div>
            )}

            {activeTab === 'myNotes' && (
              <div className="bg-gray-50 p-4 rounded-lg text-gray-700">
                <h3 className="text-md font-semibold mb-3">My Notes</h3>
                 <p>{myNotes}</p> {/* Display notes placeholder */}
                {/* You would load and display the actual user notes here, maybe with an editable area */}
              </div>
            )}
          </div>
        </div>
      </main>

       {/* Footer based on web design */}
       {/* Note: This footer is more complex than the one in RecordingList.jsx */}
      <footer className="bg-[#1A365D] text-gray-300 py-8">
          <div className="container mx-auto px-4 grid grid-cols-1 md:grid-cols-4 gap-8">
              <div>
                  <h4 className="text-white text-lg font-semibold mb-4">AudioScholar</h4>
                   <p className="text-sm">
                      Transform your learning experience with AI-powered lecture notes and summaries.
                   </p>
              </div>
              <div>
                  <h4 className="text-white text-lg font-semibold mb-4">Product</h4>
                  <ul className="space-y-2 text-sm">
                      <li><a href="#" className="hover:text-indigo-400">Features</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Pricing</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Use Cases</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Support</a></li>
                  </ul>
              </div>
              <div>
                  <h4 className="text-white text-lg font-semibold mb-4">Company</h4>
                  <ul className="space-y-2 text-sm">
                      <li><a href="#" className="hover:text-indigo-400">About</a></li>
                       <li><a href="#" className="hover:text-indigo-400">Blog</a></li> {/* Assuming a blog exists */}
                      <li><a href="#" className="hover:text-indigo-400">Careers</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Contact</a></li>
                  </ul>
              </div>
              <div>
                  <h4 className="text-white text-lg font-semibold mb-4">Legal</h4>
                   <ul className="space-y-2 text-sm">
                      <li><a href="#" className="hover:text-indigo-400">Terms</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Privacy</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Security</a></li>
                      <li><a href="#" className="hover:text-indigo-400">Cookies</a></li>
                  </ul>
              </div>
          </div>
           <div className="container mx-auto px-4 mt-8 text-center text-gray-500 text-sm">
              &copy; {new Date().getFullYear()} AudioScholar. All rights reserved.
          </div>
      </footer>
    </div>
  );
};

export default Summaryview;